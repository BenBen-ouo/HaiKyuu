/*
控制發球流程狀態，包含等待發球、球已發出與進入正式來回。
負責鎖住發球方 backPlayer 輸入，避免發球 Space 被誤判成撲球或攻擊。
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

    public boolean shouldUpdateBall() {
        return state == ServeState.SERVE_LAUNCHED || state == ServeState.IN_PLAY;
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
        if (state == ServeState.WAITING_FOR_SERVE || state == ServeState.SERVE_LAUNCHED) {
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

        if (state == ServeState.WAITING_FOR_SERVE) {
            updateReadyState(servingInput, justPressedServe);
        } else if (state == ServeState.SERVE_LAUNCHED) {
            ServeInputLocker.lockBackPlayer(servingInput);
        } else if (state == ServeState.IN_PLAY) {
            updatePostServeReleaseLock(servingInput);
        }

        lastServePressed = servingInput.servePressed;
    }

    public void updateAfterTeams() {
        if (state == ServeState.SERVE_LAUNCHED && isServerOnGround()) {
            state = ServeState.IN_PLAY;
        }
    }

    public void updateAfterBall() {
        // 目前先移除跳飄拋球流程；保留入口讓之後跳發／拋球狀態可接回來。
    }

    public void finishFrame() {
        serveLaunchedThisFrame = false;
    }

    private void updateReadyState(TeamInput servingInput, boolean justPressedServe) {
        // 發球位置與球的位置只在 Server 進入 WAITING_FOR_SERVE 時決定；Client 不在此自行重設。
        ServeInputLocker.lockBackPlayer(servingInput);

        if (justPressedServe) {
            launchServe(servingInput.serveType);
        }
    }

    private void launchServe(ServeType serveType) {
        ballController.launchServe(serveType, redServing);

        state = ServeState.SERVE_LAUNCHED;
        waitForPostServeSpaceRelease = true;
        serveLaunchedThisFrame = true;

        model.resetCounters();
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
    }
}
