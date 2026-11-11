import java.util.Random;
import model.GameModel;
import model.ControlInput;
import model.ai.AiDifficulty;
import model.ai.AiObservation;
import model.ai.RuleBasedAi;
import model.player.PlayerAction;
import model.serve.ServeState;

/** 真正推進正式模型，不以隨機成功率替代觸球。 */
public final class AiMatchTest {
    // 提前回防後曾在 48000 幀仍為 28:28；保留正式兩分差與完賽驗收，延長有限模擬。
    private static final int MATCH_SIMULATION_FRAMES = 96000;
    public static void main(String[] args) {
        testDifficulty();
        testMirroredInput();
        testObservationDoesNotMutateModel();
        testSlidersAndController();
        testDefenseScenarios();
        for (int skill : new int[]{10, 40, 41, 50, 60, 61, 95}) {
            simulate(skill, MATCH_SIMULATION_FRAMES);
        }
        System.out.println("AiMatchTest passed");
    }
    private static void simulate(int skill, int frames) {
        GameModel model = new GameModel();
        RuleBasedAi red = new RuleBasedAi(true, new AiDifficulty(skill, skill), new Random(210));
        RuleBasedAi blue = new RuleBasedAi(false, new AiDifficulty(skill, skill), new Random(211));
        int receives = 0, sets = 0, spikes = 0, blocks = 0, dives = 0, serves = 0, matches = 0;
        int airSets = 0, jumpSets = 0, multiAttacks = 0, twoAttacks = 0, jumpServes = 0, lobs = 0, repeatBlocks = 0;
        java.util.Set<Integer> strikeHeights = new java.util.HashSet<>();
        int[] hitters = new int[4];
        int[] tempoCounts = new int[3];
        int[][] takeoff = new int[2][4];
        int[] lastSet = {-1000, -1000};
        long[] decisionNanos = new long[frames];
        for (int frame = 0; frame < frames; frame++) {
            var before = model.getServeHandler().getState();
            PlayerAction[] old = {model.redTeam.backPlayer.getAction(), model.redTeam.wingSpiker.getAction(),
                    model.blueTeam.backPlayer.getAction(), model.blueTeam.wingSpiker.getAction()};
            var contacts = model.captureRallyContacts();
            boolean[][] wasJumping = {new boolean[4], new boolean[4]};
            for (int role = 0; role < 4; role++) {
                wasJumping[0][role] = model.redTeam.getPlayers()[role].jumping;
                wasJumping[1][role] = model.blueTeam.getPlayers()[role].jumping;
            }
            long decisionStart = System.nanoTime();
            ControlInput a = red.update(AiObservation.capture(model, true));
            ControlInput b = blue.update(AiObservation.capture(model, false));
            decisionNanos[frame] = System.nanoTime() - decisionStart;
            check(a.servePressed == a.backJump && a.backJump == a.backDive
                    && a.spikeFlat == a.backRight && a.airSetModifier == (a.backLeft && a.backRight),
                    "AI 操作遵循既有鍵盤共鍵關係");
            model.update(a, b);
            if (model.getHitCount(true) == 1 && contacts.red().hitCount() != 1) lastSet[0] = -1000;
            if (model.getHitCount(false) == 1 && contacts.blue().hitCount() != 1) lastSet[1] = -1000;
            if (model.getServeHandler().getState() != before && model.getServeHandler().hasLaunchedServe()) serves++;
            if (model.didFirstServeReceptionThisFrame()) receives++;
            if (model.didSetterContactThisFrame() || model.didAirSetContactThisFrame()) sets++;
            if ((model.didSetterContactThisFrame() || model.didAirSetContactThisFrame())
                    && model.getLastHitTeam() != null && model.getHitCount(model.getLastHitTeam()) == 2) {
                lastSet[model.getLastHitTeam() ? 0 : 1] = frame;
            }
            if (model.didAirSetContactThisFrame()) airSets++;
            if (model.didSetterContactThisFrame()
                    && (model.redTeam.setter.jumping || model.blueTeam.setter.jumping)) jumpSets++;
            if (before == ServeState.JUMP_TOSS && model.getServeHandler().hasLaunchedServe()) jumpServes++;
            if ((!contacts.red().blockUsed() && model.hasBlocked(true))
                    || (!contacts.blue().blockUsed() && model.hasBlocked(false))) blocks++;
            var teams = new model.player.Team[]{model.redTeam, model.blueTeam};
            for (int side = 0; side < 2; side++) {
                var players = teams[side].getPlayers();
                int preparing = 0;
                for (int role = 0; role < 4; role++) {
                    if (players[role].jumping && !wasJumping[side][role]) takeoff[side][role] = frame;
                    if (role != 1 && (players[role].getAction() == PlayerAction.ATTACK_READY
                            || players[role].getAction() == PlayerAction.RUN_APPROACH)) preparing++;
                    if (players[role].getAction() == PlayerAction.ATTACK_SWING
                            && players[role].hasValidAttackAttemptThisFrame()) {
                        spikes++; hitters[role]++;
                        strikeHeights.add((int) model.ball.y);
                        // Inspect the attack input; a same-frame rebound can change ball velocity.
                        if (before != ServeState.JUMP_TOSS && (side == 0 ? a : b).spikeLob) lobs++;
                        // 沒有本次球權第二球舉球時，不拿上一輪的舉球時間推算節奏。
                        if (before != ServeState.JUMP_TOSS && lastSet[side] >= 0) {
                            int offset = takeoff[side][role] - lastSet[side];
                            tempoCounts[offset < -5 ? 0 : offset <= 5 ? 1 : 2]++;
                        }
                    }
                }
                if (preparing >= 3) multiAttacks++;
                if (preparing >= 2) twoAttacks++;
                if (teams[side].quickAttacker.getCountedBlockJumps() >= 2) repeatBlocks++;
                if (players[0].getAction() == PlayerAction.DIVE && old[side * 2] != PlayerAction.DIVE) dives++;
                if (players[3].getAction() == PlayerAction.DIVE && old[side * 2 + 1] != PlayerAction.DIVE) dives++;
            }
            if (!Double.isFinite(model.ball.x) || !Double.isFinite(model.ball.y)) throw new AssertionError("Invalid physics");
            if (model.matchOver) { matches++; model.restart(); red.reset(); blue.reset(); }
        }
        System.out.printf("skill=%d serves=%d receptions=%d sets=%d attacks=%d hitters=%s blocks=%d dives=%d matches=%d score=%d:%d%n",
                skill, serves, receives, sets, spikes, java.util.Arrays.toString(hitters), blocks, dives, matches, model.redScore, model.blueScore);
        System.out.printf("airSets=%d jumpSets=%d twoAttackFrames=%d threeAttackFrames=%d jumpServes=%d lobs=%d repeatBlockFrames=%d strikeHeights=%d%n",
                airSets, jumpSets, twoAttacks, multiAttacks, jumpServes, lobs, repeatBlocks, strikeHeights.size());
        System.out.println("actualTempos=" + java.util.Arrays.toString(tempoCounts));
        java.util.Arrays.sort(decisionNanos);
        System.out.printf("twoAiDecision mean=%.3fms p95=%.3fms%n",
                java.util.Arrays.stream(decisionNanos).average().orElse(0) / 1e6,
                decisionNanos[(int) (frames * .95)] / 1e6);
        if (serves < 10 || receives < 5 || sets < 2) throw new AssertionError("AI must actually serve, receive and set");
        check(matches > 0 && spikes > 0 && dives > 0, "AI 完成實際攻防並結束正式比賽");
        if (skill <= 20) check(lobs == 0, "入門不使用吊球");
        if (skill <= 20) check(hitters[2] == 0 && tempoCounts[0] == 0,
                "入門不使用快攻配球或負節奏");
        if (skill <= 40) check(jumpSets == 0 && repeatBlocks == 0,
                "普通以下不使用 Setter 跳舉或補攔");
        if (skill < 61) check(airSets == 0 && multiAttacks == 0, "困難以下不使用假扣真舉或三人進攻");
        if (skill < 41) check(jumpServes == 0 && twoAttacks == 0, "普通以下不使用跳發或雙人進攻");
        if (skill == 50) check(twoAttacks > 0, "普通難度實際組織雙人進攻");
        if (skill == 95) check(multiAttacks > 0, "高難度在可達球路下實際組織三人進攻");
    }

    private static void testDifficulty() {
        for (int value = 2; value <= 100; value++) {
            check(AiDifficulty.reactionSeconds(value) < AiDifficulty.reactionSeconds(value - 1), "能力連續影響反應時間");
        }
        var habits = new model.ai.OpponentHabits();
        double before = habits.attackerWeight(2);
        habits.recordAttack(2, 0, 3);
        check(habits.attackerWeight(2) > before && habits.attackerWeight(2) < .5, "單次樣本不支配預測");
        for (int i = 0; i < 30; i++) habits.recordAttack(2, 0, 3);
        check(habits.attackerWeight(2) > .8 && habits.lobRate() > .8, "足夠樣本逐漸改變權重");
        habits.reset();
        check(habits.attackSamples() == 0 && Math.abs(habits.attackerWeight(2) - before) < 1e-9, "新場清除習慣");
    }

    private static void testMirroredInput() {
        GameModel game = new GameModel();
        RuleBasedAi left = new RuleBasedAi(true, AiDifficulty.DEFAULT, new Random(19));
        RuleBasedAi right = new RuleBasedAi(false, AiDifficulty.DEFAULT, new Random(19));
        for (int frame = 0; frame < 2000; frame++) {
            AiObservation canonical = AiObservation.capture(game, true);
            ControlInput a = left.update(canonical);
            ControlInput b = right.update(canonical);
            check(a.backLeft == b.backRight && a.backRight == b.backLeft
                    && a.backJump == b.backJump && a.backDive == b.backDive
                    && a.wingActionPressed == b.wingActionPressed && a.mbActionPressed == b.mbActionPressed
                    && a.setterJump == b.setterJump && a.airSetModifier == b.airSetModifier
                    && a.servePressed == b.servePressed && a.serveType == b.serveType
                    && a.spikeFlat == b.spikeFlat && a.spikeShort == b.spikeShort && a.spikeLob == b.spikeLob,
                    "相同場況／亂數的兩側決策完全鏡像，球路模式不二次翻轉");
            game.update(a, new ControlInput());
            if (game.matchOver) { game.restart(); left.reset(); right.reset(); }
        }
        left.reset();
        check(left.learnedAttacks() == 0 && left.difficulty().equals(AiDifficulty.DEFAULT), "R 保留難度清除歷史");
    }

    private static void testObservationDoesNotMutateModel() {
        GameModel game = new GameModel();
        double x = game.ball.x, vx = game.ball.vx, backX = game.redTeam.backPlayer.x;
        var ai = new RuleBasedAi(true, AiDifficulty.DEFAULT, new Random(9));
        for (int i = 0; i < 120; i++) ai.update(AiObservation.capture(game, true));
        check(game.ball.x == x && game.ball.vx == vx && game.redTeam.backPlayer.x == backX
                && game.redScore == 0 && game.getServeHandler().isWaitingForServe(), "AI 單獨決策不能移動角色、球或裁決");
        game.matchOver = true;
        var finished = ai.update(AiObservation.capture(game, true));
        check(ai.state() == RuleBasedAi.State.FINISHED && !finished.servePressed
                && !finished.wingActionPressed && !finished.mbActionPressed, "賽末不再發球或啟動攻擊");
    }

    private static void testSlidersAndController() {
        for (int skill : new int[]{1, 20, 40, 41, 60, 61, 100}) {
            int jumps = 0;
            for (int seed = 0; seed < 160; seed++) {
                if (serveDecision(new AiDifficulty(50, skill), seed).jump()) jumps++;
            }
            if (skill < 41) check(jumps == 0, "進攻 40 以下禁止跳發");
            if (skill >= 61) check(jumps > 0, "解鎖後可實際選擇跳發");
        }
        int weakAdvanced = 0, strongAdvanced = 0;
        for (int seed = 0; seed < 80; seed++) {
            var lowDefense = serveDecision(new AiDifficulty(1, 65), seed);
            var highDefense = serveDecision(new AiDifficulty(100, 65), seed);
            check(lowDefense.equals(highDefense), "防守拉條不改變相同進攻設定的發球決策");
            var weak = serveDecision(new AiDifficulty(50, 1), seed);
            var strong = serveDecision(new AiDifficulty(50, 100), seed);
            if (weak.advanced()) weakAdvanced++;
            if (strong.advanced()) strongAdvanced++;
        }
        check(strongAdvanced > weakAdvanced, "進攻能力連續影響進階發球使用率");
        var defenseGame = new GameModel();
        defenseGame.getServeHandler().setRedServing(false);
        defenseGame.getServeHandler().setWaitingForServe(false);
        defenseGame.ball.x = 350;
        defenseGame.ball.y = 320;
        defenseGame.ball.vx = -4;
        defenseGame.ball.vy = 2;
        var defenseObservation = AiObservation.capture(defenseGame, true);
        var weakAttack = new RuleBasedAi(true, new AiDifficulty(65, 1), new Random(22));
        var strongAttack = new RuleBasedAi(true, new AiDifficulty(65, 100), new Random(22));
        for (int tick = 0; tick < 60; tick++) {
            var a = weakAttack.update(defenseObservation);
            var b = strongAttack.update(defenseObservation);
            check(a.backLeft == b.backLeft && a.backRight == b.backRight
                    && a.backDive == b.backDive && a.mbActionPressed == b.mbActionPressed
                    && a.wingActionPressed == b.wingActionPressed,
                    "進攻拉條不改變同場況／同防守能力的誤差與反應序列");
        }
        for (boolean aiRed : new boolean[]{true, false}) {
            var model = new GameModel();
            var keyboard = new controller.KeyboardController();
            var ai = new RuleBasedAi(aiRed, new AiDifficulty(73, 42), new Random(12));
            var controller = new controller.GameController(model, keyboard, null, ai);
            java.awt.Component source = new javax.swing.JPanel();
            int aiMb = aiRed ? java.awt.event.KeyEvent.VK_L : java.awt.event.KeyEvent.VK_NUMPAD6;
            int humanMb = aiRed ? java.awt.event.KeyEvent.VK_NUMPAD6 : java.awt.event.KeyEvent.VK_L;
            keyboard.keyPressed(new java.awt.event.KeyEvent(source, java.awt.event.KeyEvent.KEY_PRESSED,
                    0, 0, aiMb, java.awt.event.KeyEvent.CHAR_UNDEFINED));
            keyboard.keyPressed(new java.awt.event.KeyEvent(source, java.awt.event.KeyEvent.KEY_PRESSED,
                    0, 0, humanMb, java.awt.event.KeyEvent.CHAR_UNDEFINED));
            controller.update();
            var bot = aiRed ? model.redTeam : model.blueTeam;
            var human = aiRed ? model.blueTeam : model.redTeam;
            check(bot.quickAttacker.getAction() == PlayerAction.IDLE
                    && human.quickAttacker.getAction() == PlayerAction.BLOCK,
                    "左右模式只接管 AI 一側，玩家操作仍有效");
            model.awardPointWithMessage(!aiRed, "IN");
            check(model.redScore + model.blueScore == 1, "AI 對戰使用正式計分");
            keyboard.keyPressed(new java.awt.event.KeyEvent(source, java.awt.event.KeyEvent.KEY_PRESSED,
                    0, 0, java.awt.event.KeyEvent.VK_R, 'R'));
            controller.update();
            check(model.redScore == 0 && model.blueScore == 0 && model.getServeHandler().isRedServing()
                    && ai.learnedAttacks() == 0 && ai.difficulty().equals(new AiDifficulty(73, 42)),
                    "R 重開清分／習慣並保留難度與紅隊先發");
        }
    }
    private static void testDefenseScenarios() {
        double[][] shots = {{450, 545, 0, 3}, {300, 300, -4, 2}, {550, 400, -7, 1}};
        for (boolean aiRed : new boolean[]{true, false}) {
            int rescued = 0;
            for (double[] shot : shots) {
                var game = new GameModel();
                game.getServeHandler().setRedServing(!aiRed);
                game.getServeHandler().setWaitingForServe(true);
                game.getServeHandler().setWaitingForServe(false);
                var team = aiRed ? game.redTeam : game.blueTeam;
                game.recordRegularHit(aiRed, team.setter);
                game.resetCounters();
                game.setLastHitTeam(!aiRed);
                game.ball.x = aiRed ? shot[0] : model.GameConfig.SCREEN_WIDTH - shot[0];
                game.ball.y = shot[1];
                game.ball.vx = aiRed ? shot[2] : -shot[2];
                game.ball.vy = shot[3];
                game.ball.syncPreviousPosition();
                var ai = new RuleBasedAi(aiRed, new AiDifficulty(100, 50), new Random(10));
                boolean received = false;
                for (int frame = 0; frame < 120; frame++) {
                    var input = ai.update(AiObservation.capture(game, aiRed));
                    game.update(aiRed ? input : new ControlInput(), aiRed ? new ControlInput() : input);
                    if (game.getHitCount(aiRed) > 0) { rescued++; received = true; break; }
                    if (game.isRallyOverForNetwork()) break;
                }
                check(received, "指定來球應經實際碰撞救起：side=" + aiRed + " shot=" + java.util.Arrays.toString(shot));
            }
            check(rescued == shots.length, "左右 AI 透過原碰撞系統救起短球、長球與回彈方向來球");
        }
    }
    private record ServeDecision(int tick, model.serve.ServeType type, boolean jump) {
        boolean advanced() { return jump || type == model.serve.ServeType.SHORT || type == model.serve.ServeType.CEILING; }
    }
    private static ServeDecision serveDecision(AiDifficulty difficulty, int seed) {
        var game = new GameModel();
        var observation = AiObservation.capture(game, true);
        var ai = new RuleBasedAi(true, difficulty, new Random(seed));
        for (int tick = 0; tick < 120; tick++) {
            var input = ai.update(observation);
            if (input.servePressed) return new ServeDecision(tick, input.serveType, input.spikeFlat);
        }
        throw new AssertionError("AI must release the serve within bounded wait");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
