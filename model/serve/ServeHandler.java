/*
控制發球流程狀態，包含等待發球、跳發拋球、球已發出與正式來回。
發球方後排平時鎖住動作；跳發拋球期間只開放第二次起跳與第三次擊球。
每次進入下一球等待發球時，會將接球方 backPlayer 回復至預設站位。
*/
package model.serve;

import model.GameModel;
import model.TeamInput;
import model.player.Player;
import model.player.PlayerPhysics;
import model.player.Team;

public class ServeHandler {
    private final GameModel model;
    private final ServeBallController ballController;
    private final ServeContactPolicy contactPolicy = new ServeContactPolicy();

    private ServeState state = ServeState.WAITING_FOR_SERVE;
    private boolean redServing = true;
    private boolean lastServePressed = false;
    private boolean waitForPostServeSpaceRelease = false;
    private boolean serveLaunchedThisFrame = false;
    private boolean jumpStarted;
    private boolean jumpShortArc;
    private boolean jumpSlowHorizontal;
    private boolean jumpLongArc;
    private boolean predictionLastServePressed;

    public ServeHandler(GameModel model) {
        this.model = model;
        this.ballController = new ServeBallController(model);
    }

    public boolean isWaitingForServe() {
        return state == ServeState.WAITING_FOR_SERVE;
    }

    public boolean isRedServing() {
        return redServing;
    }

    public boolean hasLaunchedServe() {
        return state == ServeState.SERVE_LAUNCHED || state == ServeState.IN_PLAY;
    }

    public boolean shouldUpdateBall() {
        return state == ServeState.JUMP_TOSS || state == ServeState.SERVE_LAUNCHED
                || state == ServeState.IN_PLAY;
    }

    public boolean shouldUseGameBackPlayerAction(boolean redSide) {
        return state == ServeState.IN_PLAY || redSide != redServing;
    }

    public boolean canTeamCollideWithBall(boolean redSide) {
        return contactPolicy.canTeamCollide(
                state,
                redSide,
                redServing,
                serveLaunchedThisFrame
        );
    }

    public void setWaitingForServe(boolean waiting) {
        state = waiting ? ServeState.WAITING_FOR_SERVE : ServeState.IN_PLAY;

        if (waiting) {
            resetReceivingBackPlayer();
            ballController.prepareServe(redServing);
        }

        resetFrameFlags();
    }

    public void setRedServing(boolean redServing) {
        this.redServing = redServing;
        resetFrameFlags();
    }

    /** 得分顯示進入鎖定階段時，先讓下一球發球員站到發球位。 */
    public void positionServerForServe() {
        ballController.positionServerForServe(redServing);
    }

    public void reset() {
        state = ServeState.WAITING_FOR_SERVE;
        redServing = !model.isPracticeMode();
        ballController.prepareServe(redServing);
        resetFrameFlags();
    }

    public ServeState getState() {
        return state;
    }

    public void applyNetworkState(ServeState state, boolean redServing) {
        this.state = state == null ? ServeState.WAITING_FOR_SERVE : state;
        this.redServing = redServing;
        lastServePressed = false;
        Player server = redServing ? model.redTeam.backPlayer : model.blueTeam.backPlayer;
        jumpStarted = this.state == ServeState.JUMP_TOSS && server.jumping;
        // IN_PLAY 可能與發球同幀出現；不能因此清除仍按住發球鍵的保護。
        if (this.state == ServeState.WAITING_FOR_SERVE) {
            waitForPostServeSpaceRelease = false;
        } else if (this.state == ServeState.SERVE_LAUNCHED) {
            waitForPostServeSpaceRelease = true;
        }
        serveLaunchedThisFrame = false;
    }

    /** 收到可靠 SERVE 事件後，鎖住發球方後排動作直到原本那次按鍵放開。 */
    public void lockNetworkPostServeBackAction() {
        waitForPostServeSpaceRelease = true;
    }

    /** Client 只預測自己的球員動作；不得在此發球或改變球與發球階段。 */
    public void filterNetworkPredictionInput(TeamInput input, boolean redSide, boolean rawServePressed) {
        if (redSide != redServing) {
            return;
        }
        boolean justPressed = rawServePressed && !predictionLastServePressed;
        predictionLastServePressed = rawServePressed;
        if (state == ServeState.JUMP_TOSS) {
            ServeInputLocker.lockBackPlayer(input);
            Player server = redSide ? model.redTeam.backPlayer : model.blueTeam.backPlayer;
            if (justPressed && !server.jumping && !jumpStarted) {
                input.backJump = true;
                jumpStarted = true;
            } else if (rawServePressed && server.jumping && jumpStarted) {
                input.backJump = true;
            }
        } else if (state == ServeState.WAITING_FOR_SERVE || state == ServeState.SERVE_LAUNCHED) {
            ServeInputLocker.lockBackPlayer(input);
            if (state == ServeState.SERVE_LAUNCHED && !rawServePressed) {
                waitForPostServeSpaceRelease = false;
            }
        } else if (waitForPostServeSpaceRelease) {
            ServeInputLocker.suppressBackActionUntilReleased(input);
            if (!rawServePressed) {
                waitForPostServeSpaceRelease = false;
            }
        }
    }

    public void updateBeforeTeams(TeamInput redInput, TeamInput blueInput) {
        serveLaunchedThisFrame = false;

        TeamInput servingInput = redServing ? redInput : blueInput;
        boolean justPressedServe = servingInput.servePressed && !lastServePressed;
        jumpShortArc = servingInput.spikeShort;
        jumpSlowHorizontal = redServing ? servingInput.backLeft : servingInput.backRight;
        jumpLongArc = redServing ? servingInput.backRight : servingInput.backLeft;

        if (state == ServeState.WAITING_FOR_SERVE) {
            updateReadyState(servingInput, justPressedServe);
        } else if (state == ServeState.JUMP_TOSS) {
            ServeInputLocker.lockBackPlayer(servingInput);
            Player server = redServing ? model.redTeam.backPlayer : model.blueTeam.backPlayer;
            if (justPressedServe && !jumpStarted && !server.jumping) {
                servingInput.backJump = true;
                jumpStarted = true;
            } else if (servingInput.servePressed && server.jumping && jumpStarted) {
                servingInput.backJump = true;
            }
        } else if (state == ServeState.SERVE_LAUNCHED) {
            ServeInputLocker.lockBackPlayer(servingInput);
        } else if (state == ServeState.IN_PLAY) {
            updatePostServeReleaseLock(servingInput);
        }

        lastServePressed = servingInput.servePressed;
    }

    public void updateAfterTeams() {
        if (state == ServeState.JUMP_TOSS) {
            Player server = redServing ? model.redTeam.backPlayer : model.blueTeam.backPlayer;
            if (server.isAttackSwinging() && server.hasValidAttackAttemptThisFrame()) {
                ballController.hitJumpServe(redServing, jumpShortArc,
                        jumpSlowHorizontal, jumpLongArc);
                server.attackHitBox.disable();
                state = ServeState.SERVE_LAUNCHED;
                serveLaunchedThisFrame = true;
                waitForPostServeSpaceRelease = true;
                model.resetCounters();
                model.resetBlockJumpCounts();
                model.resetServeReception();
            }
        }
        if (state == ServeState.SERVE_LAUNCHED && isServerOnGround()) {
            state = ServeState.IN_PLAY;
        }
    }

    public void updateAfterBall() {
        // 拋球落地由 RallyScorer 判發球犯規；這裡不改變 Server 的球路。
    }

    public void finishFrame() {
        serveLaunchedThisFrame = false;
    }

    private void updateReadyState(TeamInput servingInput, boolean justPressedServe) {
        // 發球位置與球的位置只在 Server 進入 WAITING_FOR_SERVE 時決定；Client 不在此自行重設。
        ServeInputLocker.lockBackPlayer(servingInput);

        if (justPressedServe) {
            if (servingInput.spikeFlat) {
                ballController.tossJumpServe(redServing);
                state = ServeState.JUMP_TOSS;
                jumpStarted = false;
            } else {
                launchServe(servingInput.serveType);
            }
        }
    }

    private void launchServe(ServeType serveType) {
        ballController.launchServe(serveType, redServing);

        state = ServeState.SERVE_LAUNCHED;
        waitForPostServeSpaceRelease = true;
        serveLaunchedThisFrame = true;

        model.resetCounters();
        model.resetBlockJumpCounts();
        model.resetServeReception();
    }

    private void updatePostServeReleaseLock(TeamInput servingInput) {
        if (!waitForPostServeSpaceRelease) {
            return;
        }

        ServeInputLocker.suppressBackActionUntilReleased(servingInput);

        if (!servingInput.servePressed) {
            waitForPostServeSpaceRelease = false;
        }
    }

    private boolean isServerOnGround() {
        Player server = redServing
                ? model.redTeam.backPlayer
                : model.blueTeam.backPlayer;

        return PlayerPhysics.isOnGround(server);
    }

    private void resetReceivingBackPlayer() {
        Team receivingTeam = redServing
                ? model.blueTeam
                : model.redTeam;

        receivingTeam.backPlayer.resetToInitial();
    }

    private void resetFrameFlags() {
        lastServePressed = false;
        waitForPostServeSpaceRelease = false;
        serveLaunchedThisFrame = false;
        jumpStarted = false;
        predictionLastServePressed = false;
    }
}
