package model.ai;

import java.util.List;
import model.rally.ContactEvent;
import model.GameConfig;
import model.GameModel;
import model.player.Player;
import model.player.PlayerAction;
import model.player.Team;
import model.player.QuickAttacker;
import model.player.HitBox;
import model.player.BlockPhase;
import model.serve.ServeState;

/** 只擷取目前可見位置、動作及已裁定觸球；不含按鍵或對手意圖。X 統一為本隊在左。 */
public record AiObservation(BallForecast ball, List<Pose> own, List<Pose> opponent,
        int ownTouches, int opponentTouches, int ownLastRole, boolean lastTouchOwn,
        boolean receptionComplete, boolean attackLocked, boolean blockUsed,
        ServeState serveState, boolean ownServing, boolean rallyOver, boolean matchOver,
        boolean netContact, boolean landing, boolean opponentAttackLocked,
        BlockView opponentBlock, List<ContactEvent> contacts, int ownScore, int opponentScore,
        String judgment, long contactStreamId) {
    public AiObservation {
        own = List.copyOf(own);
        opponent = List.copyOf(opponent);
        contacts = List.copyOf(contacts);
        if (own.size() != 4 || opponent.size() != 4) throw new IllegalArgumentException("Four roles required");
    }
    /** 攔網框的唯讀配置；維持目前旋轉與鏡像，不用 AI 自存另一份 Team 數值。 */
    public record BlockShape(double offsetX, double offsetY, double width, double height, double rotation) {}
    public record BlockView(BlockShape hands, BlockShape head, int handsStartFrames, int handsEndFrames, boolean used) {}
    /** 角色順序沿用 Team.getPlayers()：0 後排、1 Setter、2 MB、3 WS。 */
    public record Pose(double x, double y, double velocityX, double velocityY,
            double width, double height, PlayerAction action, boolean jumping, boolean diving,
            double receiveOffsetX, double receiveOffsetY, double receiveWidth, double receiveHeight,
            double attackOffsetX, double attackOffsetY, double attackWidth, double attackHeight,
            int blockJumpCount) {
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
                model.didBallLandThisFrame(), model.isAttackLocked(!redSide),
                blockView(redSide ? model.blueTeam.quickAttacker : model.redTeam.quickAttacker,
                        redSide, model.hasBlocked(!redSide)), model.contactEvents(),
                redSide ? model.redScore : model.blueScore, redSide ? model.blueScore : model.redScore,
                model.transientMessage, model.contactStreamId());
    }
    private static BlockView blockView(QuickAttacker mb, boolean redSide, boolean used) {
        return new BlockView(blockShape(mb.blockHitBox, mb.imageWidth, redSide),
                blockShape(mb.headBlockHitBox, mb.imageWidth, redSide),
                mb.framesUntilBlockPhase(BlockPhase.HANDS_EXTENDED), mb.framesUntilBlockPhase(BlockPhase.RETRACTING), used);
    }
    private static BlockShape blockShape(HitBox box, double imageWidth, boolean redSide) {
        return new BlockShape(redSide ? box.offsetX : imageWidth - box.offsetX - box.width,
                box.offsetY, box.width, box.height, redSide ? box.rotationDegrees : -box.rotationDegrees);
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
                p.attackHitBox.offsetY, p.attackHitBox.width, p.attackHitBox.height,
                p instanceof QuickAttacker mb ? mb.getCountedBlockJumps() : 0);
    }
    private static int roleOf(Team team, Player player) {
        Player[] roles = team.getPlayers();
        for (int i = 0; i < roles.length; i++) if (roles[i] == player) return i;
        return -1;
    }
}
