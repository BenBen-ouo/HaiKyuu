package model.ai;

import java.util.Random;
import model.ControlInput;
import model.GameModel;
import model.TeamInput;
import model.player.PlayerAction;
import model.rally.RallyContactHandler;

/** 以可觀察操作驗證解鎖邊界，不讀取規劃器的私有旗標。 */
public final class AiPolicyTest {
    public static void main(String[] args) {
        testOffenseUnlocks();
        testGapSelection();
        testBlockUnlocks();
        System.out.println("AiPolicyTest passed");
    }

    private static GameModel receivedByOpponent(boolean red) {
        GameModel game = new GameModel();
        game.getServeHandler().setRedServing(!red);
        game.getServeHandler().setWaitingForServe(false);
        var own = red ? game.redTeam : game.blueTeam;
        var opponent = red ? game.blueTeam : game.redTeam;
        game.recordRegularHit(red, own.setter);
        game.recordRegularHit(!red, opponent.backPlayer);
        game.ball.x = red ? 750 : 450;
        game.ball.y = 200;
        game.ball.vx = red ? -5 : 5;
        game.ball.vy = 0;
        game.ball.syncPreviousPosition();
        return game;
    }

    private static void testOffenseUnlocks() {
        for (int ability : new int[]{20, 21, 40, 41, 100}) {
            int quick = 0, jumps = 0, negative = 0;
            for (boolean red : new boolean[]{true, false}) {
                for (int seed = 0; seed < 80; seed++) {
                    GameModel game = new GameModel();
                    game.getServeHandler().setRedServing(!red);
                    game.getServeHandler().setWaitingForServe(true);
                    game.getServeHandler().setWaitingForServe(false);
                    var team = red ? game.redTeam : game.blueTeam;
                    game.ball.x = team.backPlayer.hitBox.getCenterX();
                    game.ball.y = team.backPlayer.hitBox.getCenterY();
                    new RallyContactHandler(game).collideTeam(team, red, new TeamInput());
                    // 較長的公開來球讓負節奏確實有起跑窗口，不要求不可達球路硬起跳。
                    game.ball.vx = red ? 3.2 : -3.2;
                    game.ball.vy = -12;
                    var planner = new OffensePlanner(new Random(seed), new OpponentHabits());
                    var difficulty = new AiDifficulty(50, ability);
                    planner.begin(AiObservation.capture(game, red), 0, difficulty);
                    boolean quickRequested = false, jumped = false;
                    int started = -1, setAt = -1;
                    for (int tick = 0; tick < 160 && !game.isRallyOverForNetwork(); tick++) {
                        var input = new ControlInput();
                        planner.update(AiObservation.capture(game, red), input, tick, difficulty);
                        if (game.getHitCount(red) == 1) quickRequested |= input.spikeShort;
                        var world = red ? input : input.mirroredHorizontally();
                        game.update(red ? world : new ControlInput(), red ? new ControlInput() : world);
                        jumped |= team.setter.jumping;
                        if (started < 0 && (team.wingSpiker.getAction() == PlayerAction.RUN_APPROACH
                                || team.backPlayer.jumping)) started = tick;
                        if (game.didSetterContactThisFrame()) { setAt = tick; break; }
                    }
                    if (quickRequested) quick++;
                    if (jumped) jumps++;
                    if (started >= 0 && setAt >= 0 && started < setAt - 5) negative++;
                }
            }
            System.out.printf("offenseUnlock ability=%d quick=%d jumpSet=%d negative=%d%n", ability, quick, jumps, negative);
            if (ability <= 20) check(quick == 0 && negative == 0, "入門不能快攻配球或負節奏");
            if (ability <= 40) check(jumps == 0, "簡單以下不能 Setter 跳舉");
            if (ability >= 21) check(quick > 0, "簡單開始會選快攻配球");
            if (ability == 100) check(jumps > 0, "已解鎖的跳舉確實執行");
        }
        testReachableNegativeTempo();
    }

    private static void testReachableNegativeTempo() {
        int early = 0;
        for (int seed = 0; seed < 100; seed++) {
            GameModel game = new GameModel();
            game.getServeHandler().setRedServing(false);
            game.getServeHandler().setWaitingForServe(true);
            game.getServeHandler().setWaitingForServe(false);
            var team = game.redTeam;
            game.ball.x = team.backPlayer.hitBox.getCenterX();
            game.ball.y = team.backPlayer.hitBox.getCenterY();
            new RallyContactHandler(game).collideTeam(team, true, new TeamInput());
            var ai = new RuleBasedAi(true, new AiDifficulty(50, 40), new Random(seed));
            int start = -1;
            for (int tick = 0; tick < 180 && !game.isRallyOverForNetwork(); tick++) {
                var input = ai.update(AiObservation.capture(game, true));
                game.update(input, new ControlInput());
                if (start < 0 && team.wingSpiker.getAction() == PlayerAction.RUN_APPROACH) start = tick;
                if (game.didSetterContactThisFrame()) {
                    if (start >= 0 && tick - start >= 42 && tick - start <= 48) early++;
                    break;
                }
            }
        }
        System.out.println("simpleNegativeStarts=" + early);
        check(early > 0, "簡單已能在可達站舉球路執行負節奏");
    }

    private static void testGapSelection() {
        GameModel first = new GameModel(), second = new GameModel();
        first.ball.x = second.ball.x = 550;
        first.ball.y = second.ball.y = 380;
        first.blueTeam.backPlayer.x = first.blueTeam.wingSpiker.x = 760;
        second.blueTeam.backPlayer.x = second.blueTeam.wingSpiker.x = 1060;
        for (int ability : new int[]{20, 40, 41, 100}) {
            int changed = 0;
            for (int seed = 0; seed < 120; seed++) {
                int a = AttackOptions.choose(AiObservation.capture(first, true), AiDifficulty.ability(ability), new Random(seed), false);
                int b = AttackOptions.choose(AiObservation.capture(second, true), AiDifficulty.ability(ability), new Random(seed), false);
                check(a >= 0 && b >= 0, "測試球路須有可用攻擊選項");
                if (a != b) changed++;
            }
            if (ability <= 40) check(changed == 0, "普通以下不依防守空檔選球路");
            else check(changed > 0, "普通開始會因防守位置不同改選球路");
        }
    }

    private static void testBlockUnlocks() {
        for (boolean red : new boolean[]{true, false}) {
            for (int ability : new int[]{20, 21, 40, 41, 100}) {
                GameModel game = receivedByOpponent(red);
                var team = red ? game.redTeam : game.blueTeam;
                team.quickAttacker.applyBlockJumpState(1, true);
                var planner = new DefensePlanner(new Random(7));
                var difficulty = new AiDifficulty(ability, 50);
                var habits = new OpponentHabits();
                planner.react(0, difficulty, habits, AiObservation.capture(game, red));
                boolean requested = false;
                for (int tick = 0; tick < 50 && !game.isRallyOverForNetwork(); tick++) {
                    var input = new ControlInput();
                    planner.update(AiObservation.capture(game, red), input, tick, difficulty, habits);
                    requested |= input.mbActionPressed;
                    var world = red ? input : input.mirroredHorizontally();
                    game.update(red ? world : new ControlInput(), red ? new ControlInput() : world);
                }
                if (ability <= 40) check(!requested && team.quickAttacker.getCountedBlockJumps() == 1,
                        "簡單以下同一輪不補攔");
                else check(requested && team.quickAttacker.getCountedBlockJumps() >= 2,
                        "普通開始能依實際模型補攔");
            }
            GameModel game = receivedByOpponent(red);
            var planner = new DefensePlanner(new Random(9));
            var habits = new OpponentHabits();
            var difficulty = new AiDifficulty(20, 50);
            planner.react(0, difficulty, habits, AiObservation.capture(game, red));
            for (int tick = 0; tick < 30; tick++) {
                var input = new ControlInput();
                planner.update(AiObservation.capture(game, red), input, tick, difficulty, habits);
                check(!input.mbActionPressed, "入門不因快球將預設第二節奏提前覆蓋");
                var world = red ? input : input.mirroredHorizontally();
                game.update(red ? world : new ControlInput(), red ? new ControlInput() : world);
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
