package model;

import java.util.Random;

public class ServeHandler {
    private enum ServeState {
        READY,
        JUMP_TOSS,
        SERVE_LAUNCHED,
        IN_PLAY
    }

    private final GameModel model;
    private final Random random = new Random();

    private ServeState state = ServeState.READY;
    private boolean redServing = true;
    private boolean lastServePressed = false;

    // 跳發第一段 D + Space 只拋球，必須放開 Space 後，下一次 Space 才能起跳。
    private boolean waitForJumpServeFirstSpaceRelease = false;

    // 發球揮臂那一下 Space / 0 不能在落地進入比賽後，立刻被解讀成撲接。
    private boolean waitForPostServeSpaceRelease = false;

    // 發球球剛被打出的同一幀，不讓發球方再被自己的球碰撞到。
    private boolean serveLaunchedThisFrame = false;

    public ServeHandler(GameModel model) {
        this.model = model;
    }

    public boolean isWaitingForServe() {
        return state == ServeState.READY;
    }

    public boolean isRedServing() {
        return redServing;
    }

    public boolean shouldUpdateBall() {
        return state == ServeState.JUMP_TOSS
                || state == ServeState.SERVE_LAUNCHED
                || state == ServeState.IN_PLAY;
    }

    public boolean shouldUseGameBackPlayerAction(boolean redSide) {
        if (state == ServeState.IN_PLAY) {
            return true;
        }

        // 發球流程中，發球方 backPlayer 的 Space 由 ServeHandler 處理，不能被轉成撲接。
        // 非發球方維持原本接球邏輯。
        return redSide != redServing;
    }

    public boolean canTeamCollideWithBall(boolean redSide) {
        if (state == ServeState.READY || state == ServeState.JUMP_TOSS) {
            return false;
        }

        // 球剛發出去但發球動作尚未完成時，先不讓發球方碰撞，避免剛發球又打到自己。
        if (state == ServeState.SERVE_LAUNCHED) {
            return redSide != redServing;
        }

        // 進入正式來回後，剛發球的同一幀仍然先擋掉發球方碰撞。
        if (serveLaunchedThisFrame && redSide == redServing) {
            return false;
        }

        return true;
    }

    public void setWaitingForServe(boolean waiting) {
        state = waiting ? ServeState.READY : ServeState.IN_PLAY;
        lastServePressed = false;
        waitForJumpServeFirstSpaceRelease = false;
        waitForPostServeSpaceRelease = false;
        serveLaunchedThisFrame = false;
    }

    public void setRedServing(boolean redServing) {
        this.redServing = redServing;
        state = ServeState.READY;
        lastServePressed = false;
        waitForJumpServeFirstSpaceRelease = false;
        waitForPostServeSpaceRelease = false;
        serveLaunchedThisFrame = false;
    }

    public void reset() {
        state = ServeState.READY;
        redServing = true;
        lastServePressed = false;
        waitForJumpServeFirstSpaceRelease = false;
        waitForPostServeSpaceRelease = false;
        serveLaunchedThisFrame = false;
    }

    public void updateBeforeTeams(TeamInput redInput, TeamInput blueInput) {
        serveLaunchedThisFrame = false;

        TeamInput currentInput = redServing ? redInput : blueInput;
        Player server = getServingTeam().backPlayer;
        boolean justPressedServe = currentInput.servePressed && !lastServePressed;

        if (state == ServeState.READY) {
            prepareServe(redServing);
            lockServingBackPlayer(currentInput);

            if (justPressedServe) {
                if (currentInput.serveType == ServeType.JUMP) {
                    startJumpServeToss(redServing);
                    waitForJumpServeFirstSpaceRelease = true;
                } else {
                    launchServeFromCurrentBall(currentInput.serveType, redServing);
                    state = ServeState.SERVE_LAUNCHED;
                    waitForPostServeSpaceRelease = true;
                    model.resetCounters();
                }
            }
        } else if (state == ServeState.JUMP_TOSS) {
            // 跳發拋球後：允許發球者左右移動與起跳，但禁止 Space 被當成撲接。
            currentInput.backDive = false;

            // 第一段 D + Space / Left + 0 只負責拋球，不允許同一次按鍵順便起跳。
            if (waitForJumpServeFirstSpaceRelease) {
                currentInput.backJump = false;

                if (!currentInput.servePressed) {
                    waitForJumpServeFirstSpaceRelease = false;
                }
            }

            // 玩家已經在空中時，再按一次 Space / 0 才是揮臂發球。
            if (!waitForJumpServeFirstSpaceRelease && justPressedServe && server.jumping) {
                launchServeFromCurrentBall(ServeType.JUMP, redServing);
                state = ServeState.SERVE_LAUNCHED;
                waitForPostServeSpaceRelease = true;
                model.resetCounters();
            }
        } else if (state == ServeState.SERVE_LAUNCHED) {
            // 球已經發出，但發球者還沒落地前，暫時不允許撲接、跳躍或二次動作。
            lockServingBackPlayer(currentInput);
        } else if (state == ServeState.IN_PLAY) {
            // 發球完成後，如果玩家還按著剛才發球用的 Space / 0，不能立刻變成撲接。
            if (waitForPostServeSpaceRelease) {
                currentInput.backJump = false;
                currentInput.backDive = false;

                if (!currentInput.servePressed) {
                    waitForPostServeSpaceRelease = false;
                }
            }
        }

        lastServePressed = currentInput.servePressed;
    }

    public void updateAfterTeams() {
        if (state == ServeState.SERVE_LAUNCHED && isServerOnGround()) {
            state = ServeState.IN_PLAY;
        }
    }

    public void updateAfterBall() {
        // 跳發拋球後，如果玩家沒有在球落地前揮臂，就回到等待發球狀態。
        if (state == ServeState.JUMP_TOSS && model.ball.y + model.ball.radius >= GameConfig.FLOOR_Y - 1) {
            state = ServeState.READY;
            waitForJumpServeFirstSpaceRelease = false;
            waitForPostServeSpaceRelease = false;
        }
    }

    public void finishFrame() {
        serveLaunchedThisFrame = false;
    }

    public void prepareServe(boolean redSide) {
        Team team = redSide ? model.redTeam : model.blueTeam;
        Player server = team.backPlayer;

        server.x = redSide ? GameConfig.RED_BACK_SERVE_X : GameConfig.BLUE_BACK_SERVE_X;
        server.y = redSide ? GameConfig.RED_BACK_SERVE_Y : GameConfig.BLUE_BACK_SERVE_Y;
        server.vx = 0;
        server.vy = 0;
        server.jumping = false;
        server.diving = false;
        server.attacking = false;
        server.blocking = false;

        placeBallForServe(redSide);
    }

    private void lockServingBackPlayer(TeamInput input) {
        input.backLeft = false;
        input.backRight = false;
        input.backJump = false;
        input.backDive = false;
    }

    private void startJumpServeToss(boolean redSide) {
        placeBallForServe(redSide);

        double targetX = redSide
                ? GameConfig.RED_JUMP_SERVE_TOSS_LANDING_X
                : GameConfig.BLUE_JUMP_SERVE_TOSS_LANDING_X;

        double[] velocity = PhysicsUtils.calculateVelocityToTarget(
                model.ball.x,
                model.ball.y,
                targetX,
                GameConfig.JUMP_SERVE_TOSS_LANDING_Y,
                GameConfig.JUMP_SERVE_TOSS_POWER,
                GameConfig.GRAVITY
        );

        model.ball.vx = velocity[0];
        model.ball.vy = velocity[1];
        state = ServeState.JUMP_TOSS;
        model.resetCounters();
    }

    private void placeBallForServe(boolean redSide) {
        Team team = redSide ? model.redTeam : model.blueTeam;
        Player server = team.backPlayer;
        Ball ball = model.ball;

        if (redSide) {
            ball.x = server.x + GameConfig.RED_SERVE_BALL_OFFSET_X;
        } else {
            ball.x = server.x + server.imageWidth - GameConfig.BLUE_SERVE_BALL_OFFSET_X;
        }

        ball.y = server.y + (redSide ? GameConfig.RED_SERVE_BALL_OFFSET_Y : GameConfig.BLUE_SERVE_BALL_OFFSET_Y);
        ball.vx = 0;
        ball.vy = 0;
    }

    private void launchServeFromCurrentBall(ServeType serveType, boolean redSide) {
        double direction = redSide ? 1.0 : -1.0;
        setServeVelocity(serveType.baseVx * direction, serveType.baseVy);
        serveLaunchedThisFrame = true;
    }

    private void setServeVelocity(double baseVx, double baseVy) {
        model.ball.vx = baseVx + randomRange(GameConfig.SERVE_RANDOM_VX_RANGE);
        model.ball.vy = baseVy + randomRange(GameConfig.SERVE_RANDOM_VY_RANGE);
    }

    private boolean isServerOnGround() {
        Player server = getServingTeam().backPlayer;
        return !server.jumping && server.y + server.imageHeight >= GameConfig.FLOOR_Y - 0.5;
    }

    private Team getServingTeam() {
        return redServing ? model.redTeam : model.blueTeam;
    }

    private double randomRange(double range) {
        return (random.nextDouble() * 2.0 - 1.0) * range;
    }
}