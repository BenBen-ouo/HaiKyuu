package model.ai;

import java.util.Random;
import model.ControlInput;
import model.GameConfig;
import model.GameModel;
import model.TeamInput;
import model.player.PlayerAction;
import model.rally.RallyContactHandler;

/** 習慣必須改變實際操作，而不只是改變統計數字。 */
public final class AiLearningTest {
    public static void main(String[] args) {
        testContextAndConfidence();
        testObservedLandingIsCountedOnce();
        testLearnedPlanKeepsGroundDefense();
        testAttackToMbCounter();
        System.out.println("AiLearningTest passed");
    }

    private static OpponentHabits deepHabits() {
        var habits = new OpponentHabits();
        for (int i = 0; i < 10; i++) {
            habits.recordAttack(1, 1, 1);
            habits.recordAttackLanding(1, 1, 113);
        }
        return habits;
    }

    private static void testContextAndConfidence() {
        var habits = new OpponentHabits();
        habits.recordAttackLanding(1, 1, 113);
        check(!habits.shouldKeepBackForReturn(100), "一次樣本不改變整體進攻安排");
        check(habits.expectedAttackLanding(1, 1, 266) < 266
                        && habits.expectedAttackLanding(0, 0, 266) == 266,
                "MB 第一節奏落點不混成後排負節奏的落點");
        habits = deepHabits();
        check(habits.shouldKeepBackForReturn(100) && !habits.shouldKeepBackForReturn(1),
                "同樣習慣依防守能力給不同的戰術影響");
        habits.reset();
        check(habits.attackLandingSamples() == 0 && habits.fastDeepRisk() == 0
                        && habits.expectedAttackLanding(1, 1, 266) == 266,
                "重開清除情境落點與風險");
    }

    private static void feedMbHistory(RuleBasedAi ai, boolean red, int rounds) {
        for (int round = 0; round < rounds; round++) {
            var game = new GameModel();
            game.getServeHandler().setRedServing(!red);
            game.getServeHandler().setWaitingForServe(true);
            ai.update(AiObservation.capture(game, red));
            game.getServeHandler().setWaitingForServe(false);
            var own = red ? game.redTeam : game.blueTeam;
            var opponent = red ? game.blueTeam : game.redTeam;
            game.recordRegularHit(red, own.setter);
            game.recordRegularHit(!red, opponent.backPlayer);
            game.ball.x = red ? 900 : 300;
            game.ball.y = 100; game.ball.vx = 0; game.ball.vy = 0;
            ai.update(AiObservation.capture(game, red));
            game.recordRegularHit(!red, opponent.setter);
            opponent.setter.playSettingAnimation();
            ai.update(AiObservation.capture(game, red));
            var jump = new TeamInput(); jump.hasFirstRegularTouch = true; jump.mbActionPressed = true;
            opponent.quickAttacker.update(jump);
            ai.update(AiObservation.capture(game, red));
            for (int t = 0; t < 30; t++) {
                game.update(new ControlInput(), new ControlInput());
                ai.update(AiObservation.capture(game, red));
            }
            var mb = opponent.quickAttacker;
            mb.startAttackSwingAnimation();
            game.ball.x = mb.attackHitBox.getCenterX(); game.ball.y = 420;
            mb.captureAttackAttemptBallOverlap(game.ball);
            game.ball.syncPreviousPosition();
            var shot = new TeamInput(); shot.spikeFlat = true;
            new RallyContactHandler(game).collideTeam(opponent, !red, shot);
            check(game.getHitCount(!red) == 3 && mb.getAction() == PlayerAction.ATTACK_SWING,
                    "習慣來自真的 MB 第三球攻擊");
            ai.update(AiObservation.capture(game, red));
            for (int t = 0; t < 70; t++) {
                game.update(new ControlInput(), new ControlInput());
                ai.update(AiObservation.capture(game, red));
            }
        }
    }

    private static void testObservedLandingIsCountedOnce() {
        for (boolean red : new boolean[]{true, false}) {
            var ai = new RuleBasedAi(red, new AiDifficulty(100, 100), new Random(3));
            feedMbHistory(ai, red, 5);
            check(ai.learnedAttackLandings() == 5 && ai.learnedAttacks() == 5,
                    "每次已確認的攻擊只記一次落地，死球反彈不重複學習：side=" + red);
            ai.reset();
            check(ai.learnedAttackLandings() == 0, "R 重開清空落點習慣");
        }
    }

    private static void testLearnedPlanKeepsGroundDefense() {
        int normalBackJumps = 0, learnedBackJumps = 0, learnedFrontAttacks = 0;
        for (boolean learned : new boolean[]{false, true}) {
            for (boolean red : new boolean[]{true, false}) {
                for (int seed = 0; seed < 48; seed++) {
                    var game = new GameModel();
                    game.getServeHandler().setRedServing(!red);
                    game.getServeHandler().setWaitingForServe(true);
                    game.getServeHandler().setWaitingForServe(false);
                    var team = red ? game.redTeam : game.blueTeam;
                    game.ball.x = team.wingSpiker.hitBox.getCenterX();
                    game.ball.y = team.wingSpiker.hitBox.getCenterY();
                    new RallyContactHandler(game).collideTeam(team, red, new TeamInput());
                    var planner = new OffensePlanner(new Random(seed), learned ? deepHabits() : new OpponentHabits());
                    var difficulty = new AiDifficulty(100, 100);
                    planner.begin(AiObservation.capture(game, red), 0, difficulty);
                    boolean backJumped = false, frontAttacked = false;
                    for (int t = 0; t < 200 && !game.isRallyOverForNetwork(); t++) {
                        var input = new ControlInput();
                        int count = game.getHitCount(red);
                        planner.update(AiObservation.capture(game, red), input, t, difficulty);
                        var world = red ? input : input.mirroredHorizontally();
                        game.update(red ? world : new ControlInput(), red ? new ControlInput() : world);
                        backJumped |= team.backPlayer.jumping;
                        if (count == 1 && game.getHitCount(red) == 2) planner.onSet(AiObservation.capture(game, red), t, difficulty);
                        frontAttacked |= team.wingSpiker.hasValidAttackAttemptThisFrame()
                                || team.quickAttacker.hasValidAttackAttemptThisFrame();
                    }
                    if (backJumped) { if (learned) learnedBackJumps++; else normalBackJumps++; }
                    if (learned && frontAttacked) learnedFrontAttacks++;
                }
            }
        }
        System.out.printf("habitPlan baselineBackJumps=%d learnedBackJumps=%d learnedFrontAttacks=%d%n",
                normalBackJumps, learnedBackJumps, learnedFrontAttacks);
        check(learnedBackJumps < normalBackJumps && learnedFrontAttacks > 0,
                "學到快速深球後減少後排空中承諾，仍由前排完成真實攻擊");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void testAttackToMbCounter() {
        int[] shots = new int[2], received = new int[2], airborne = new int[2];
        double[] backAtShot = new double[2];
        for (int learned = 0; learned < 2; learned++) {
            for (boolean red : new boolean[]{true, false}) {
                for (int seed = 0; seed < 24; seed++) {
                    var game = new GameModel();
                    var ai = new RuleBasedAi(red, new AiDifficulty(100, 100), new Random(seed));
                    if (learned == 1) feedMbHistory(ai, red, 10);
                    game.getServeHandler().setRedServing(!red);
                    game.getServeHandler().setWaitingForServe(true);
                    ai.update(AiObservation.capture(game, red));
                    game.getServeHandler().setWaitingForServe(false);
                    var team = red ? game.redTeam : game.blueTeam;
                    var opponent = red ? game.blueTeam : game.redTeam;
                    game.ball.x = team.wingSpiker.hitBox.getCenterX();
                    game.ball.y = team.wingSpiker.hitBox.getCenterY();
                    new RallyContactHandler(game).collideTeam(team, red, new TeamInput());
                    var counter = new MbCounter(!red, seed);
                    boolean hit = false, usedBlockAtHit = false;
                    for (int tick = 0; tick < 900 && !game.isRallyOverForNetwork(); tick++) {
                        var own = ai.update(AiObservation.capture(game, red));
                        var other = counter.update(game);
                        game.update(red ? own : other, red ? other : own);
                        if (!hit && opponent.quickAttacker.getAction() == PlayerAction.ATTACK_SWING
                                && opponent.quickAttacker.hasValidAttackAttemptThisFrame()) {
                            shots[learned]++;
                            backAtShot[learned] += AiObservation.capture(game, red).own().get(0).centerX();
                            if (team.backPlayer.jumping) airborne[learned]++;
                            usedBlockAtHit = game.hasBlocked(red);
                            hit = true;
                        }
                        if (hit && (game.getHitCount(red) > 0 || !usedBlockAtHit && game.hasBlocked(red))) {
                            received[learned]++;
                            break;
                        }
                    }
                }
            }
        }
        System.out.printf("mbCounter fresh=%d/%d airborne=%d learned=%d/%d airborne=%d%n",
                received[0], shots[0], airborne[0], received[1], shots[1], airborne[1]);
        System.out.printf("backAtCounter=%.1f learned=%.1f%n",backAtShot[0]/shots[0],backAtShot[1]/shots[1]);
        check(shots[0] >= 10 && shots[1] >= 10, "連續攻防必須真的進入對手 MB 快攻，不能用提前得分略過");
        check(received[1] == shots[1], "學習後仍能在連續攻防中接起或攔住實際 MB 快攻");
        check(backAtShot[1] / shots[1] < backAtShot[0] / shots[0],
                "重複深球習慣應讓後排在快攻出手以前就往深區準備");
    }

    /** 對手接發用正式 AI，進攻固定為後排假起跳＋Setter 快配＋MB 第一節奏平打。 */
    private static final class MbCounter {
        private final boolean red;
        private final RuleBasedAi receiver;
        private int tick, previousTouches, setAt = -1;
        private boolean fakeStarted;
        MbCounter(boolean red, int seed) {
            this.red = red;
            receiver = new RuleBasedAi(red, new AiDifficulty(100, 1), new Random(seed + 900));
        }
        ControlInput update(GameModel game) {
            tick++;
            var o = AiObservation.capture(game, red);
            var defense = receiver.update(o);
            if (o.ownTouches() == 1 && previousTouches != 1) { fakeStarted = false; setAt = -1; }
            if (o.ownTouches() == 2 && previousTouches != 2) setAt = tick;
            previousTouches = o.ownTouches();
            if (o.ownTouches() == 0 || !o.lastTouchOwn()) return defense;
            var input = new ControlInput();
            if (o.ownTouches() == 1) {
                input.spikeShort = true;
                if (!fakeStarted && o.own().get(0).canStart()) {
                    input.backJump = input.backDive = input.servePressed = true;
                    fakeStarted = true;
                }
            } else if (o.ownTouches() == 2) {
                input.mbActionPressed = tick == setAt || o.own().get(2).y()
                        + o.own().get(2).attackOffsetY() < 450;
                input.spikeFlat = input.backRight = true;
            }
            return red ? input : input.mirroredHorizontally();
        }
    }
}
