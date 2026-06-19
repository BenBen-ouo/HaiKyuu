package model;

public class ServeHandler {
    private final GameModel model;
    private final ServeBallController ballController;
    private final ServeContactPolicy contactPolicy = new ServeContactPolicy();

    private ServeState state = ServeState.READY;
    private boolean redServing = true;
    private boolean lastServePressed = false;
    private boolean waitForPostServeSpaceRelease = false;
    private boolean serveLaunchedThisFrame = false;

    public ServeHandler(GameModel model) {
        this.model = model;
        this.ballController = new ServeBallController(model);
    }

    public boolean isWaitingForServe() {
        return state == ServeState.READY;
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
        return contactPolicy.canTeamCollide(state, redSide, redServing, serveLaunchedThisFrame);
    }

    public void setWaitingForServe(boolean waiting) {
        state = waiting ? ServeState.READY : ServeState.IN_PLAY;
        resetFrameFlags();
    }

    public void setRedServing(boolean redServing) {
        this.redServing = redServing;
        resetFrameFlags();
    }

    public void reset() {
        state = ServeState.READY;
        redServing = true;
        resetFrameFlags();
    }

    public void updateBeforeTeams(TeamInput redInput, TeamInput blueInput) {
        serveLaunchedThisFrame = false;

        TeamInput servingInput = redServing ? redInput : blueInput;
        boolean justPressedServe = servingInput.servePressed && !lastServePressed;

        if (state == ServeState.READY) {
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
        // 目前先移除跳飄拋球流程；保留入口讓之後跳發/拋球狀態可接回來。
    }

    public void finishFrame() {
        serveLaunchedThisFrame = false;
    }

    private void updateReadyState(TeamInput servingInput, boolean justPressedServe) {
        ballController.prepareServe(redServing);
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
        Player server = redServing ? model.redTeam.backPlayer : model.blueTeam.backPlayer;
        return PlayerPhysics.isOnGround(server);
    }

    private void resetFrameFlags() {
        lastServePressed = false;
        waitForPostServeSpaceRelease = false;
        serveLaunchedThisFrame = false;
    }
}
