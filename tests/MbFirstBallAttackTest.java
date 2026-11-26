import java.util.Random;
import model.ControlInput;
import model.GameConfig;
import model.GameModel;
import model.TeamInput;
import model.ai.AiDifficulty;
import model.ai.AiObservation;
import model.ai.RuleBasedAi;
import model.player.PlayerAction;
import model.rally.ContactEvent;

/** 正常來回第一球的真實起跳、攻擊、鎖定與左右／預測／快照行為。 */
public final class MbFirstBallAttackTest {
    public static void main(String[] args) {
        testManualAndInputRelease();
        testUnreachableAndServe();
        testNetworkPrediction();
        testAiFirstAttack();
        testCopyAndReset();
        System.out.println("MbFirstBallAttackTest passed");
    }
    private static GameModel incoming(boolean red, boolean normalRally, double x, double y) {
        var game = new GameModel();
        game.getServeHandler().setRedServing(!red);
        game.getServeHandler().setWaitingForServe(true);
        game.getServeHandler().setWaitingForServe(false);
        if (normalRally) {
            var own = red ? game.redTeam : game.blueTeam;
            var opponent = red ? game.blueTeam : game.redTeam;
            game.recordRegularHit(red, own.backPlayer);
            game.recordRegularHit(!red, opponent.backPlayer);
        }
        game.ball.x = red ? x : GameConfig.SCREEN_WIDTH - x;
        game.ball.y = y; game.ball.vx = game.ball.vy = 0;
        game.ball.syncPreviousPosition();
        return game;
    }
    private static void step(GameModel game, boolean red, boolean pressed) {
        var input = new ControlInput(); input.mbActionPressed = pressed;
        game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
    }
    private static void testManualAndInputRelease() {
        for (boolean red : new boolean[]{true, false}) {
            var game = incoming(red, true, 590, 300);
            var mb = (red ? game.redTeam : game.blueTeam).quickAttacker;
            check(game.canMbAttackFirstBall(red) && game.getHitCount(red) == 0, "第一球尚未接起且不是接發球");
            int contact = mb.firstBallAttackContactFrames(game.ball);
            check(contact >= 2, "原攻擊框有真實可達窗口");
            step(game, red, true);
            check(mb.getAction() == PlayerAction.ATTACK_READY && mb.attackHitBox.enabled
                    && !mb.isHeadBlockHitBoxActive() && !mb.isBlockHitBoxActive(), "起跳準備攻擊而非攔網");
            check(mb.getCountedBlockJumps() == 0, "攻擊不占用攔網起跳次數");
            for (int tick = 1; tick <= contact + 2; tick++) step(game, red, true);
            check(game.getHitCount(red) == 0 && mb.getAction() != PlayerAction.ATTACK_SWING,
                    "只長按起跳鍵不能自動揮臂或擊球");

            game = incoming(red, true, 590, 300); mb = (red ? game.redTeam : game.blueTeam).quickAttacker;
            step(game, red, true); step(game, red, false);
            boolean hit = false;
            for (int tick = 0; tick < 65 && !game.isRallyOverForNetwork(); tick++) {
                step(game, red, true);
                for (var e : game.contactEvents()) if (e.redSide() == red && e.kind() == ContactEvent.Kind.ATTACK) {
                    check(e.role() == 2 && e.touch() == 1, "MB 扣擊確實計本隊第一觸球"); hit = true;
                }
                if (hit) break;
                check(mb.getAction() != PlayerAction.ATTACK_SWING, "沒命中不能只播揮臂");
            }
            check(hit && game.getHitCount(red) == 1 && game.isAttackLocked(red), "實際第一球攻擊與扣球鎖定");
            check((red ? game.ball.vx : -game.ball.vx) > 0, "扣球朝向對場");
        }
    }
    private static void testUnreachableAndServe() {
        for (boolean red : new boolean[]{true, false}) {
            for (double x : new double[]{300, 650}) {
                var game = incoming(red, true, x, 300);
                var mb = (red ? game.redTeam : game.blueTeam).quickAttacker;
                check(mb.firstBallAttackContactFrames(game.ball) < 0, "開網或對場配球不可當貼網第一球");
                step(game, red, true);
                check(mb.getAction() == PlayerAction.BLOCK, "不可達時保留原攔網動作");
            }
            var serve = incoming(red, false, 590, 300);
            var mb = (red ? serve.redTeam : serve.blueTeam).quickAttacker;
            check(!serve.canMbAttackFirstBall(red), "對方發球不允許第一球扣擊");
            step(serve, red, true);
            check(mb.getAction() == PlayerAction.BLOCK, "接發時同鍵仍為攔網，攔到發球仍由原犯規規則裁決");
        }
    }
    private static void testNetworkPrediction() {
        for (boolean red : new boolean[]{true, false}) {
            var server = incoming(red, true, 590, 300);
            var client = incoming(red, true, 590, 300);
            var input = new ControlInput(); input.mbActionPressed = true;
            double x = client.ball.x, y = client.ball.y, vx = client.ball.vx, vy = client.ball.vy;
            step(server, red, true);
            client.updateForNetworkPrediction(input, red);
            var serverMb = (red ? server.redTeam : server.blueTeam).quickAttacker;
            var clientMb = (red ? client.redTeam : client.blueTeam).quickAttacker;
            check(serverMb.getAction() == clientMb.getAction() && clientMb.getAction() == PlayerAction.ATTACK_READY,
                    "Server 與本地 Client 使用同一個起跳判斷");
            client.updateForNetworkPrediction(new ControlInput(), red);
            for (int tick = 0; tick < 30; tick++) client.updateForNetworkPrediction(input, red);
            check(client.ball.x == x && client.ball.y == y && client.ball.vx == vx && client.ball.vy == vy
                    && client.getHitCount(red) == 0 && clientMb.getAction() != PlayerAction.ATTACK_SWING,
                    "Client 預測不扣球、不計次、不自行播放成功揮臂");
        }
    }
    private static void testAiFirstAttack() {
        for (boolean red : new boolean[]{true, false}) {
            for (int ability : new int[]{20, 60, 100}) {
                int hits = 0;
                for (int seed = 0; seed < 12; seed++) {
                    var game = incoming(red, true, 590, 240);
                    // 對手殘留的揮臂不能讓舊攔網計畫搶走已可達的第一球攻擊窗口。
                    (red ? game.blueTeam : game.redTeam).wingSpiker.startAttackSwingAnimation();
                    var ai = new RuleBasedAi(red, new AiDifficulty(100, ability), new Random(seed));
                    for (int tick = 0; tick < 130 && !game.isRallyOverForNetwork(); tick++) {
                        var input = ai.update(AiObservation.capture(game, red));
                        if (input.mbActionPressed) check(ai.state() == RuleBasedAi.State.ATTACKING,
                                "第一球攻擊意圖須有對應的 AI 狀態");
                        game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                        boolean hit = game.contactEvents().stream().anyMatch(e -> e.redSide() == red && e.role() == 2
                                && e.kind() == ContactEvent.Kind.ATTACK && e.touch() == 1);
                        if (hit) {
                            hits++;
                            ai.update(AiObservation.capture(game, red));
                            check(ai.statistics().details().keySet().stream().anyMatch(d -> d.key().own() && d.key().role() == 2
                                    && d.key().touch() == 1 && d.context().setting() == model.ai.AiMatchStatistics.Setting.NONE
                                    && d.context().tempo() == model.ai.AiMatchStatistics.Tempo.UNKNOWN),
                                    "第一球獨立記錄，不沿用上一輪舉球節奏");
                            check(ai.statistics().blocks().predictions() == 0, "第一球攻擊起跳不冒充攔網預判");
                            check(ai.statistics().plans().get(model.ai.AiMatchStatistics.Tactic.ATTACK).completed() == 1,
                                    "真正命中第一球才記進攻安排完成");
                            break;
                        }
                    }
                }
                check(hits > 0, "AI 必須真正完成貼網第一球扣擊，不是只起跳");
                System.out.printf("MB first attack side=%s skill=%d hits=%d/12%n", red, ability, hits);
            }
        }
    }
    private static void testCopyAndReset() {
        var input = new TeamInput(); input.canMbAttackFirstBall = true;
        check(input.copy().canMbAttackFirstBall && input.mirroredHorizontally().canMbAttackFirstBall,
                "模型上下文複製保留第一球能力，方向不重複翻轉");
        var game = incoming(true, true, 590, 300); game.restart();
        check(!game.canMbAttackFirstBall(true), "整場重開不得沿用前球第一球能力");
        step(game, true, true);
        check(game.redTeam.quickAttacker.getAction() == PlayerAction.BLOCK, "重用上下文不殘留第一球攻擊資格");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
