package model.ai;

import java.util.Random;
import model.ControlInput;
import model.GameConfig;
import model.GameModel;
import model.player.PlayerAction;

/** 以公開場況、實際操作與碰球結果驗證誤差分布及短球分工。 */
public final class AiDefenseBalanceTest {
    public static void main(String[] args) {
        testDistributions();
        testBlockWaitsForApex();
        testStandingWingAndDelayedDive();
        testNetDeflection();
        testLeaveOutBall();
        testMbDirectSecond();
        System.out.println("AiDefenseBalanceTest passed");
    }

    private static void testDistributions() {
        int[] large = new int[2], small = new int[2], fast = new int[2];
        for (int group = 0; group < 2; group++) {
            var random = new Random(20261111);
            int ability = group == 0 ? 1 : 100;
            int positive = 0;
            for (int i = 0; i < 20_000; i++) {
                double error = AiDifficulty.receiveError(ability, random);
                check(Math.abs(error) <= 60, "所有難度共用 60px 硬上限");
                if (Math.abs(error) > 20) large[group]++; else small[group]++;
                if (error > 0) positive++;
                int reaction = AiDifficulty.reactionFrames(ability, random);
                check(reaction >= 9, "不快於 0.15 秒");
                if (reaction == 9) fast[group]++;
            }
            check(positive > 9_500 && positive < 10_500, "正負方向分布對稱");
            check(large[group] > 0 && small[group] > 0, "每個難度都有小誤差與大誤判");
        }
        check(large[0] > large[1] * 4 && small[1] > small[0], "難度改變分布，並非硬性縮放每次誤差");
        check(fast[0] == 0 && fast[1] > 0 && fast[1] < 4_000, "高能力偶爾快速反應，非固定最快");
        check(AiDifficulty.reactionSeconds(100) == .20, "專家通常以 0.2 秒為基準");
        var habits = new OpponentHabits();
        var flight = new BallForecast(650, 420, -GameConfig.FLAT_SPIKE_HORIZONTAL_SPEED,
                GameConfig.FLAT_SPIKE_VELOCITY_Y);
        double unseen = habits.flightFamiliarity(flight);
        for (int i = 0; i < 10; i++) habits.recordAttack(1, 1, OpponentHabits.observedDirection(flight));
        check(habits.flightFamiliarity(flight) > unseen, "已觀察球路逐步熟悉");
        habits.reset();
        check(habits.flightFamiliarity(flight) == unseen, "新比賽清空熟悉度");
    }

    private static GameModel game(boolean red) {
        var game = new GameModel();
        game.getServeHandler().setRedServing(!red);
        game.getServeHandler().setWaitingForServe(false);
        game.redTeam.resetAllPlayers();
        game.blueTeam.resetAllPlayers();
        var own = red ? game.redTeam : game.blueTeam;
        var opponent = red ? game.blueTeam : game.redTeam;
        game.recordRegularHit(red, own.setter);
        game.recordRegularHit(!red, opponent.backPlayer);
        return game;
    }

    private static void ball(GameModel game, boolean red, double x, double y, double vx, double vy) {
        game.ball.x = red ? x : GameConfig.SCREEN_WIDTH - x;
        game.ball.y = y;
        game.ball.vx = red ? vx : -vx;
        game.ball.vy = vy;
        game.ball.syncPreviousPosition();
    }

    private static void testBlockWaitsForApex() {
        for (boolean red : new boolean[]{true, false}) {
            var game = game(red);
            var opponent = red ? game.blueTeam : game.redTeam;
            var blockInput = new model.TeamInput();
            blockInput.mbActionPressed = true;
            opponent.quickAttacker.update(blockInput);
            var planner = new DefensePlanner(new Random(14));
            var difficulty = new AiDifficulty(100, 100);
            var habits = new OpponentHabits();
            ball(game, red, 650, 300, -5, -3);
            planner.react(0, difficulty, habits, AiObservation.capture(game, red));
            for (int tick = 0; tick < 80; tick++) {
                var input = new ControlInput();
                planner.update(AiObservation.capture(game, red), input, tick, difficulty, habits);
                check(!input.mbActionPressed, "接球未過最高點時，不被上一輪攔網跳躍騙起跳");
            }
            ball(game, red, 650, 300, -5, 1);
            boolean blocked = false;
            for (int tick = 80; tick < 150; tick++) {
                var input = new ControlInput();
                planner.update(AiObservation.capture(game, red), input, tick, difficulty, habits);
                blocked |= input.mbActionPressed;
            }
            check(blocked, "過最高點後仍能依來球決定攔網");
            planner.reset();
            ball(game, red, 650, 300, -5, -3);
            var input = new ControlInput();
            planner.update(AiObservation.capture(game, red), input, 200, difficulty, habits);
            check(!input.mbActionPressed, "新球重設最高點觀察");
        }
    }

    private static void testStandingWingAndDelayedDive() {
        for (boolean red : new boolean[]{true, false}) {
            var game = game(red);
            var team = red ? game.redTeam : game.blueTeam;
            var opponent = red ? game.blueTeam : game.redTeam;
            game.recordRegularHit(!red, opponent.setter);
            game.recordRegularHit(!red, opponent.quickAttacker);
            var o = AiObservation.capture(game, red);
            double wx = o.own().get(3).receiveX();
            ball(game, red, wx, 250, 0, 0);
            check(DefenseForecast.wingStandingCanReceive(AiObservation.capture(game, red), 0), "垂直落入 WS 站立框");
            var planner = new DefensePlanner(new Random(22));
            planner.reset();
            var habits = new OpponentHabits();
            var difficulty = new AiDifficulty(81, 50);
            boolean received = false;
            for (int tick = 0; tick < 100 && !game.isRallyOverForNetwork(); tick++) {
                var input = new ControlInput();
                planner.update(AiObservation.capture(game, red), input, tick, difficulty, habits);
                check(!input.wingActionPressed && !input.backDive, "站接窗口有效時兩人都不撲");
                var world = red ? input : input.mirroredHorizontally();
                game.update(red ? world : new ControlInput(), red ? new ControlInput() : world);
                if (game.getLastHitter(red) == team.wingSpiker) { received = true; break; }
            }
            check(received, "WS 透過原本碰撞真的站接成功 side=" + red + " x=" + game.ball.x
                    + " y=" + game.ball.y + " count=" + game.getHitCount(red)
                    + " last=" + game.getLastHitter(red));

            // 平行於站立框前方落下，必須滑行接球，但現在撲仍太早。
            game = game(red);
            wx = AiObservation.capture(game, red).own().get(3).receiveX();
            ball(game, red, wx + 110, 180, 0, 0);
            o = AiObservation.capture(game, red);
            check(!DefenseForecast.wingStandingCanReceive(o, 0), "此球不在站立框");
            int delay = DefenseForecast.wingDiveDelay(o, 0);
            check(delay > 0, "高球有可延後的撲球窗口");

            // 配球仍在對方 MB 的實際空中攻擊可達範圍，不能直接當成吊球。
            ball(game, red, 609, 470, -1, -3);
            check(DefenseForecast.opponentCanAttack(AiObservation.capture(game, red)), "保留對手 MB 的改變球路機會");
            ball(game, red, 400, 500, -1, 2);
            check(!DefenseForecast.opponentCanAttack(AiObservation.capture(game, red)), "已離開對方攻擊範圍就恢復接球");
        }
    }

    private static void testLeaveOutBall() {
        for (boolean red : new boolean[]{true, false}) {
            var game = game(red);
            ball(game, red, 70, 450, -4, 2);
            check(DefenseForecast.leaveOutBall(AiObservation.capture(game, red)), "對手最後碰球且已界外遠離時不救");
            var input = new ControlInput();
            new DefensePlanner(new Random(1)).update(AiObservation.capture(game, red), input, 100,
                    new AiDifficulty(100, 100), new OpponentHabits());
            check(!input.backLeft && !input.backRight && !input.backDive && !input.wingActionPressed,
                    "不產生追救操作");
            var opponent = red ? game.blueTeam : game.redTeam;
            game.recordBlock(!red, opponent.quickAttacker);
            for (int t = 0; t < 60 && !game.isRallyOverForNetwork(); t++) {
                game.update(new ControlInput(), new ControlInput());
            }
            check((red ? game.redScore : game.blueScore) == 1, "對手 touch out 沿用正式判決，無須救球即可得分 side="
                    + red + " score=" + game.redScore + ":" + game.blueScore + " ball=" + game.ball.x + "," +game.ball.y);
            game = game(red);
            ball(game, red, 70, 450, 4, 2);
            check(!DefenseForecast.leaveOutBall(AiObservation.capture(game, red)), "仍朝場內飛不能直接放掉");
            game.recordRegularHit(red, (red ? game.redTeam : game.blueTeam).backPlayer);
            ball(game, red, 70, 450, -4, 2);
            check(!DefenseForecast.leaveOutBall(AiObservation.capture(game, red)), "自身最後觸球不誤認可得分");
        }
    }

    private static void testNetDeflection() {
        for (boolean red : new boolean[]{true, false}) {
            int netHits = 0, receptions = 0;
            for (int seed = 0; seed < 12; seed++) {
                var game = new GameModel();
                game.getServeHandler().setRedServing(!red);
                game.getServeHandler().setWaitingForServe(false);
                game.setLastHitTeam(!red);
                ball(game, red, GameConfig.NET_X + 2, GameConfig.NET_TOP_Y - GameConfig.BALL_RADIUS - 1, -2, 4);
                var ai = new RuleBasedAi(red, new AiDifficulty(81, 50), new Random(seed));
                boolean hitNet = false;
                for (int t = 0; t < 120 && !game.isRallyOverForNetwork(); t++) {
                    var input = ai.update(AiObservation.capture(game, red));
                    check(!(input.wingActionPressed && input.backDive), "彈網後不讓兩人同時啟動撲救");
                    game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                    hitNet |= game.didBallHitNetThisFrame();
                    if (game.getHitCount(red) > 0) { receptions++; break; }
                }
                if (hitNet) netHits++;
            }
            check(netHits == 12 && receptions > 0, "必須真的彈網且能重新分工救起，而非只測假造軌跡");
            System.out.println("netDeflection side=" + red + " received=" + receptions + "/12");
        }
    }

    private static void testMbDirectSecond() {
        for (boolean red : new boolean[]{true, false}) {
            int hits = 0;
            for (int seed = 0; seed < 24; seed++) {
                var game = game(red);
                var own = red ? game.redTeam : game.blueTeam;
                game.recordRegularHit(red, own.setter);
                ball(game, red, 591, 500, 0, -5);
                var plannerHabits = new OpponentHabits();
                var planner = new OffensePlanner(new Random(seed), plannerHabits, new AiMatchLearning(red, plannerHabits));
                var difficulty = new AiDifficulty(50, 100);
                planner.begin(AiObservation.capture(game, red), 0, difficulty);
                for (int t = 0; t < 100 && !game.isRallyOverForNetwork(); t++) {
                    var input = new ControlInput();
                    planner.update(AiObservation.capture(game, red), input, t, difficulty);
                    var world = red ? input : input.mirroredHorizontally();
                    game.update(red ? world : new ControlInput(), red ? new ControlInput() : world);
                    if (game.getHitCount(red) >= 2 && own.quickAttacker.getAction() == PlayerAction.ATTACK_SWING
                            && own.quickAttacker.hasValidAttackAttemptThisFrame()) { hits++; break; }
                }
            }
            check(hits > 0, "Setter 第一球貼網時 MB 真的打第二球：side=" + red);
            System.out.println("mbDirectSecond side=" + red + " hits=" + hits);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
