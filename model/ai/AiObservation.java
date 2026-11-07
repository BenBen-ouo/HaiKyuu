package model.ai;

import java.util.List;
import model.GameConfig;
import model.GameModel;
import model.player.Player;
import model.player.PlayerAction;
import model.player.Team;
import model.serve.ServeState;

/** 只擷取目前可見位置、動作及已裁定觸球；不含按鍵或對手意圖。X 統一為本隊在左。 */
public record AiObservation(BallForecast ball, List<Pose> own, List<Pose> opponent,
        int ownTouches, int opponentTouches, int ownLastRole, boolean lastTouchOwn,
        boolean receptionComplete, boolean attackLocked, boolean blockUsed,
        ServeState serveState, boolean ownServing, boolean rallyOver, boolean matchOver,
        boolean netContact, boolean landing) {
    public AiObservation {
        own = List.copyOf(own);
        opponent = List.copyOf(opponent);
        if (own.size() != 4 || opponent.size() != 4) throw new IllegalArgumentException("Four roles required");
    }
    /** 角色順序沿用 Team.getPlayers()：0 後排、1 Setter、2 MB、3 WS。 */
    public record Pose(double x, double y, double velocityX, double velocityY,
            double width, double height, PlayerAction action, boolean jumping, boolean diving,
            double receiveOffsetX, double receiveOffsetY, double receiveWidth, double receiveHeight,
            double attackOffsetX, double attackOffsetY, double attackWidth, double attackHeight) {
        public double centerX() { return x + width / 2; }
        public double receiveX() { return x + receiveOffsetX + receiveWidth / 2; }
        public double receiveY() { return y + receiveOffsetY + receiveHeight / 2; }
        public boolean canStart() {
            return !jumping && !diving && (action == PlayerAction.IDLE || action == PlayerAction.RUN_LOOP);
        }
    }
    public static AiObservation capture(GameModel model, boolean redSide) {
        var ball = model.ball;
        return new AiObservation(new BallForecast(redSide ? ball.x : GameConfig.SCREEN_WIDTH - ball.x,
                ball.y, redSide ? ball.vx : -ball.vx, ball.vy),
                poses(redSide ? model.redTeam : model.blueTeam, redSide),
                poses(redSide ? model.blueTeam : model.redTeam, redSide),
                model.getHitCount(redSide), model.getHitCount(!redSide),
                roleOf(redSide ? model.redTeam : model.blueTeam, model.getLastHitter(redSide)),
                model.getLastHitTeam() != null && model.getLastHitTeam() == redSide,
                model.isServeReceptionComplete(), model.isAttackLocked(redSide), model.hasBlocked(redSide),
                model.getServeHandler().getState(), model.getServeHandler().isRedServing() == redSide,
                model.isRallyOverForNetwork(), model.matchOver, model.didBallHitNetThisFrame(),
                model.didBallLandThisFrame());
    }
    private static List<Pose> poses(Team team, boolean redSide) {
        return java.util.Arrays.stream(team.getPlayers()).map(p -> pose(p, redSide)).toList();
    }
    private static Pose pose(Player p, boolean redSide) {
        return new Pose(redSide ? p.x : GameConfig.SCREEN_WIDTH - p.x - p.imageWidth,
                p.y, redSide ? p.vx : -p.vx, p.vy, p.imageWidth, p.imageHeight,
                p.getAction(), p.jumping, p.diving,
                redSide ? p.hitBox.offsetX : p.imageWidth - p.hitBox.offsetX - p.hitBox.width,
                p.hitBox.offsetY, p.hitBox.width, p.hitBox.height,
                redSide ? p.attackHitBox.offsetX : p.imageWidth - p.attackHitBox.offsetX - p.attackHitBox.width,
                p.attackHitBox.offsetY, p.attackHitBox.width, p.attackHitBox.height);
    }
    private static int roleOf(Team team, Player player) {
        Player[] roles = team.getPlayers();
        for (int i = 0; i < roles.length; i++) if (roles[i] == player) return i;
        return -1;
    }
}
