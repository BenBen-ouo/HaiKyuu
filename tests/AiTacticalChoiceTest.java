package model.ai;

import java.util.Random;
import model.ControlInput;
import model.GameConfig;
import model.GameModel;
import model.TeamInput;
import model.player.PlayerAction;
import model.rally.RallyContactHandler;

/** 固定亂數、公開動作與正式模型驗證節奏分工及回彈選路。 */
public final class AiTacticalChoiceTest {
    public static void main(String[] args) {
        testEasyTargetsReceivers();
        testAggressiveBlock();
        testMixedPreparation();
        testBackWalksBeforeJump();
        testReboundAndRecovery();
        testCoverChangesChoice();
        System.out.println("AiTacticalChoiceTest passed");
    }

    private static void testEasyTargetsReceivers() {
        for (boolean red : new boolean[]{true, false}) {
            var game = new GameModel();
            var opponents = red ? game.blueTeam : game.redTeam;
            // 固定對場深區，普通與短扣球的通過位置不同，不能只抽相同球種冒充照顧玩家。
            double x = 960;
            for (var p : new model.player.Player[]{opponents.backPlayer, opponents.wingSpiker}) {
                p.x = red ? x - p.imageWidth / 2 : GameConfig.SCREEN_WIDTH - x - p.imageWidth / 2;
            }
            game.ball.x = red ? 550 : 650;
            game.ball.y = 380;
            var o = AiObservation.capture(game, red);
            for (int ability : new int[]{1, 20, 40, 41, 60, 61, 100}) {
                int[] chosen = new int[5];
                for (int seed = 0; seed < 400; seed++) {
                    int shot = AttackOptions.choose(o, AiDifficulty.ability(ability), new Random(seed * 7919L), 3, false, new AiMatchStatistics(), null);
                    check(shot >= 0, "可用球路不應因難度消失");
                    chosen[shot]++;
                }
                // 此處普通扣球較深，短扣球落向較空的前場。
                if (ability <= 40) check(chosen[0] > chosen[2], "入門／簡單較常打向已有防守者的深區");
                if (ability >= 41) check(chosen[2] > chosen[0], "普通以上維持攻擊空檔選路");
            }
        }
    }

    private static GameModel received(boolean red) {
        var game = new GameModel();
        game.getServeHandler().setRedServing(!red);
        game.getServeHandler().setWaitingForServe(false);
        game.redTeam.resetAllPlayers(); game.blueTeam.resetAllPlayers();
        var own = red ? game.redTeam : game.blueTeam;
        var opp = red ? game.blueTeam : game.redTeam;
        game.recordRegularHit(red, own.setter);
        game.recordRegularHit(!red, opp.backPlayer);
        return game;
    }

    private static void testAggressiveBlock() {
        for (boolean red : new boolean[]{true, false}) {
            int ordinary = 0, high = 0;
            for (int ability : new int[]{60, 61, 100}) {
                int immediate = 0;
                for (int seed = 0; seed < 40; seed++) {
                    var game = received(red);
                    var opponent = red ? game.blueTeam : game.redTeam;
                    var start = new TeamInput(); start.mbActionPressed = true; start.hasFirstRegularTouch = true;
                    opponent.quickAttacker.update(start);
                    var hold = new TeamInput(); hold.hasFirstRegularTouch = true;
                    for (int t = 0; t < 20; t++) opponent.quickAttacker.update(hold);
                    game.ball.x = opponent.quickAttacker.attackHitBox.getCenterX();
                    game.ball.y = opponent.quickAttacker.attackHitBox.getCenterY();
                    game.ball.vx = 0; game.ball.vy = -1;
                    var difficulty = new AiDifficulty(ability, 50);
                    var habits = new OpponentHabits();
                    var planner = new DefensePlanner(new Random(seed));
                    planner.reset();
                    planner.react(0, difficulty, habits, AiObservation.capture(game, red));
                    var beforeApex = new ControlInput();
                    planner.update(AiObservation.capture(game, red), beforeApex, 100, difficulty, habits);
                    check(!beforeApex.mbActionPressed, "積極攔網仍不能繞過最高點門檻");
                    game.ball.vy = 1;
                    var input = new ControlInput();
                    planner.update(AiObservation.capture(game, red), input, 101, difficulty, habits);
                    if (input.mbActionPressed) immediate++;
                }
                if (ability == 60) ordinary = immediate;
                else { check(immediate == 40, "61 起對可見的迫近扣球修正舊起跳時間"); high += immediate; }
            }
            check(high / 2 > ordinary, "新攔網積極性只從高難度開始");
        }
    }

    private static void testMixedPreparation() {
        int[] mixedSides = new int[2];
        for (boolean red : new boolean[]{true, false}) {
            int mixed = 0, doubleNegative = 0, triple = 0;
            for (int seed = 0; seed < 160; seed++) {
                var game = new GameModel();
                game.getServeHandler().setRedServing(!red);
                game.getServeHandler().setWaitingForServe(true);
                game.getServeHandler().setWaitingForServe(false);
                var own = red ? game.redTeam : game.blueTeam;
                game.ball.x = own.backPlayer.hitBox.getCenterX();
                game.ball.y = own.backPlayer.hitBox.getCenterY();
                new RallyContactHandler(game).collideTeam(own, red, new TeamInput());
                check(game.getHitCount(red) == 1, "準備案例需先完成接發球");
                game.ball.vx = red ? 3.2 : -3.2; game.ball.vy = -12;
                var plannerHabits = new OpponentHabits();
                var planner = new OffensePlanner(new Random(seed * 7919L), plannerHabits, new AiMatchLearning(red, plannerHabits));
                var difficulty = new AiDifficulty(50, 100);
                planner.begin(AiObservation.capture(game, red), 0, difficulty);
                int backStart = -1, wingStart = -1, setAt = -1, roles = 0;
                for (int t = 0; t < 220 && !game.isRallyOverForNetwork(); t++) {
                    var input = new ControlInput();
                    planner.update(AiObservation.capture(game, red), input, t, difficulty);
                    // 此案例只觀察準備排程：起跳後先不按第二次攻擊，讓後續節奏有時間實際開始。
                    if (own.backPlayer.jumping) input.backJump = input.backDive = false;
                    if (own.wingSpiker.jumping) input.wingActionPressed = false;
                    if (own.quickAttacker.jumping) input.mbActionPressed = false;
                    var world = red ? input : input.mirroredHorizontally();
                    game.update(red ? world : new ControlInput(), red ? new ControlInput() : world);
                    if (backStart < 0 && own.backPlayer.jumping) { backStart = t; roles |= 1; }
                    if (wingStart < 0 && own.wingSpiker.getAction() == PlayerAction.RUN_APPROACH) { wingStart = t; roles |= 2; }
                    if (own.quickAttacker.jumping) roles |= 4;
                    if (setAt < 0 && game.didSetterContactThisFrame()) {
                        setAt = t; planner.onSet(AiObservation.capture(game, red), t, difficulty);
                    }
                }
                if (setAt >= 0 && backStart >= 0 && wingStart >= 0) {
                    if ((backStart < setAt) != (wingStart < setAt)) mixed++;
                    if (backStart < setAt && wingStart < setAt) doubleNegative++;
                }
                if (roles == 7) triple++;
            }
            System.out.printf("mixedPreparation side=%s mixed=%d doubleNegative=%d triple=%d%n",red,mixed,doubleNegative,triple);
            check(mixed > 0 && mixed > doubleNegative, "實際出現混合節奏，且比雙負節奏常見");
            check(triple > 0, "節奏分配仍允許三名角色在同一次組織中實際啟動");
            mixedSides[red ? 0 : 1] = mixed;
        }
        check(mixedSides[0] == mixedSides[1], "左右共用相同節奏安排");
    }

    private static void testCoverChangesChoice() {
        for (int striker : new int[]{0, 2, 3}) {
            boolean changed = false;
            for (int age : new int[]{14, 22, 30, 38}) {
                var game = received(true);
                var own = game.redTeam;
                var block = new TeamInput(); block.mbActionPressed = true;
                game.blueTeam.quickAttacker.update(block);
                for (int i = 1; i < age; i++) game.blueTeam.quickAttacker.update(new TeamInput());
                for (int coverRole : new int[]{0, 3}) {
                    if (coverRole == striker) continue;
                    var receiver = own.getPlayers()[coverRole];
                    for (int x = 520; x <= 590 && !changed; x += 10) {
                        for (int y = 340; y <= 470 && !changed; y += 10) {
                            game.ball.x = x; game.ball.y = y;
                            for (int px = 120; px <= 420 && !changed; px += 50) {
                                for (int role : new int[]{0, 3}) {
                                    var p = own.getPlayers()[role];
                                    p.jumping = true; p.y = GameConfig.PLAYER_BASE_Y - 100; p.vy = -8;
                                }
                                if (coverRole == 0) receiver.x = px;
                                var noCover = AiObservation.capture(game, true);
                                receiver.jumping = false; receiver.y = GameConfig.PLAYER_BASE_Y; receiver.vy = 0;
                                var covered = AiObservation.capture(game, true);
                                for (int seed = 0; seed < 12; seed++) {
                                    long sample = seed * 7919L;
                                    int a = AttackOptions.choose(noCover, 1, new Random(sample), striker, false, new AiMatchStatistics(), null);
                                    int b = AttackOptions.choose(covered, 1, new Random(sample), striker, false, new AiMatchStatistics(), null);
                                    if (a >= 0 && b >= 0 && a != b) {
                                        int below = AttackOptions.choose(noCover, AiDifficulty.ability(60), new Random(sample), striker, false, new AiMatchStatistics(), null);
                                        int belowCovered = AttackOptions.choose(covered, AiDifficulty.ability(60), new Random(sample), striker, false, new AiMatchStatistics(), null);
                                        check(below == belowCovered, "60 以下不啟用回彈補救選路");
                                        changed = true; break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (changed) break;
            }
            check(changed, "只改補救者的實際可達狀態，就應影響高難度球路選擇：striker=" + striker);
        }
    }

    private static void testBackWalksBeforeJump() {
        for (boolean red : new boolean[]{true, false}) {
            int jumps = 0, testedPeaks = 0;
            for (int seed = 0; seed < 80; seed++) {
                var game = new GameModel();
                game.getServeHandler().setRedServing(!red);
                game.getServeHandler().setWaitingForServe(true);
                game.getServeHandler().setWaitingForServe(false);
                var own = red ? game.redTeam : game.blueTeam;
                // 後排從很深的後場準備，不能再把目前位置直接當假起跳位置。
                double deepX = GameConfig.COURT_LEFT_X + 30 - own.backPlayer.imageWidth / 2;
                own.backPlayer.x = red ? deepX : GameConfig.SCREEN_WIDTH - deepX - own.backPlayer.imageWidth;
                game.ball.x = own.wingSpiker.hitBox.getCenterX();
                game.ball.y = own.wingSpiker.hitBox.getCenterY();
                new RallyContactHandler(game).collideTeam(own, red, new TeamInput());
                // 本例只驗證扣球／假攻擊；空中舉球另由 AiTacticsTest、AiStatisticsTest 驗證。
                var difficulty = new AiDifficulty(60, 60);
                var plannerHabits = new OpponentHabits();
                var planner = new OffensePlanner(new Random(seed * 7919L), plannerHabits, new AiMatchLearning(red, plannerHabits));
                planner.begin(AiObservation.capture(game, red), 0, difficulty);
                boolean trackPeak = false;
                for (int t = 0; t < 240 && !game.isRallyOverForNetwork(); t++) {
                    var before = AiObservation.capture(game, red);
                    var input = new ControlInput();
                    planner.update(before, input, t, difficulty);
                    if (own.backPlayer.jumping) input.backJump = input.backDive = false;
                    if (own.wingSpiker.jumping) input.wingActionPressed = false;
                    if (own.quickAttacker.jumping) input.mbActionPressed = false;
                    var world = red ? input : input.mirroredHorizontally();
                    game.update(red ? world : new ControlInput(), red ? new ControlInput() : world);
                    if (!before.own().get(0).jumping() && own.backPlayer.jumping) {
                        jumps++; trackPeak = true;
                        double center = before.own().get(0).centerX();
                        check(center < GameConfig.NET_X - GameConfig.THREE_METER_PX,
                                "AI 攻擊／假起跳仍在三米線合法後場側");
                        check(center > GameConfig.COURT_LEFT_X + GameConfig.THREE_METER_PX,
                                "不能在深後場原地起跳假攻擊");
                    }
                    if (trackPeak && own.backPlayer.vy >= 0) {
                        var peak = AiObservation.capture(game, red);
                        var p = peak.own().get(0);
                        check(p.x() + p.attackOffsetX() + p.attackWidth() + GameConfig.BALL_RADIUS
                                        >= peak.own().get(1).centerX() - AiTuning.MOVEMENT_DEAD_ZONE_PX,
                                "依實際空中運動驗證最高點攻擊框能對準 Setter 橫向位置");
                        testedPeaks++; trackPeak = false;
                    }
                    if (game.didSetterContactThisFrame() && game.getHitCount(red) == 2)
                        planner.onSet(AiObservation.capture(game, red), t, difficulty);
                }
            }
            check(jumps > 0 && testedPeaks > 0, "必須真的前進後起跳並抵達最高點，不是全面禁止後排進攻");
            System.out.printf("backPreparation side=%s jumps=%d peaks=%d%n", red, jumps, testedPeaks);
        }
    }

    private static void testReboundAndRecovery() {
        for (boolean red : new boolean[]{true, false}) {
            var game = received(red);
            var own = red ? game.redTeam : game.blueTeam;
            var opp = red ? game.blueTeam : game.redTeam;
            game.recordRegularHit(red, own.wingSpiker);
            var block = new TeamInput(); block.mbActionPressed = true;
            opp.quickAttacker.update(block);
            for (int i = 0; i < 24; i++) opp.quickAttacker.update(new TeamInput());
            var mb = opp.quickAttacker;
            var box = mb.activeBlockHitBox();
            check(box != null, "實際攔網框啟用");
            // 同高的左側碰撞，反射必須朝本隊；不靠 AI 成功率旗標冒充反彈。
            game.ball.x = box.getCenterX() + (red ? -1 : 1) * (box.width / 2 + GameConfig.BALL_RADIUS / 2);
            game.ball.y = box.getCenterY();
            game.ball.vx = red ? 17 : -17; game.ball.vy = 2;
            game.ball.syncPreviousPosition();
            var o = AiObservation.capture(game, red);
            var predicted = ReboundForecast.evaluate(o, 17, 2, 3);
            new RallyContactHandler(game).collideTeam(opp, !red, new TeamInput());
            check(game.hasBlocked(!red) && (red ? game.ball.vx < 0 : game.ball.vx > 0), "正式碰撞真的反射回本隊");
            check(predicted.blocked() && predicted.returnsOwnSide(), "估計與這次實際攔網方向一致");
            double savedX = own.backPlayer.x;
            own.backPlayer.jumping = true; own.backPlayer.y = GameConfig.PLAYER_BASE_Y - 5;
            own.backPlayer.vy = 5; own.backPlayer.vx = 0;
            var lateJump = AiObservation.capture(game, red).own().get(0);
            var groundBall = new BallForecast(lateJump.receiveX(), 450, 0, 2);
            check(ReboundForecast.canRecover(lateJump, groundBall, 0, false), "假跑即將落地且趕得上，仍算補救者");
            own.backPlayer.y = GameConfig.PLAYER_BASE_Y - 100; own.backPlayer.vy = -8;
            check(!ReboundForecast.canRecover(AiObservation.capture(game, red).own().get(0), groundBall, 0, false),
                    "仍在高空、來不及落地時不算有人補救");
            own.backPlayer.x = savedX;
            check(new ReboundForecast.Result(true, true, true, false, 200).score(false)
                    > new ReboundForecast.Result(true, true, false, false, 200).score(false), "有人補救的回後場路線加分");
            check(new ReboundForecast.Result(true, true, false, true, 500).score(false)
                    > new ReboundForecast.Result(true, true, false, false, 500).score(false), "WS 趕得上的短回彈加分");
            var dead = new ReboundForecast.Result(true, true, false, false, 500);
            check(dead.score(true) < dead.score(false), "雙負節奏時更加避開無人可救的封死路線");
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
