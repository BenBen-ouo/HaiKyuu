import java.util.Random;
import model.ControlInput;
import model.GameConfig;
import model.GameModel;
import model.TeamInput;
import model.ai.AiDifficulty;
import model.ai.AiObservation;
import model.ai.RuleBasedAi;
import model.player.PlayerAction;
import model.rally.RallyContactHandler;

/** 以實際接球後的進攻轉換率驗收，不把跑完整場當成進攻有效。 */
public final class AiTacticsTest {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("mb")) {
            testMbLobPreference();
            return;
        }
        if (args.length > 0 && args[0].equals("defense")) {
            testDeepBallDefense();
            testRetreatDuringOpponentOrganization();
            return;
        }
        testDeepBallDefense();
        testRetreatDuringOpponentOrganization();
        testOwnAttackCover();
        testMbLobPreference();
        testReceiveCommitmentAndZone();
        testBackRescuesAfterEarlyWingDive();
        testOpenSetAvoidsMb();
        testBackCanReplaceSetter();
        testBasicSecondTouchWithoutAirSet();
        testApproachForecastMatchesMovement();
        testNormalServeConversion();
        testJumpServeUsesReachableVariant();
        for (int skill : new int[]{61, 100}) {
            for (int firstRole : new int[]{0, 1}) {
                int attempts = 0, attacks = 0, cleared = 0, faults = 0, jumpSets = 0;
                java.util.List<Integer> standingLeads = new java.util.ArrayList<>();
                java.util.List<Integer> secondStarts = new java.util.ArrayList<>();
                for (boolean red : new boolean[]{true, false}) {
                    for (int seed = 0; seed < 24; seed++) {
                        GameModel game = new GameModel();
                        game.getServeHandler().setRedServing(!red);
                        game.getServeHandler().setWaitingForServe(true);
                        game.getServeHandler().setWaitingForServe(false);
                        var team = red ? game.redTeam : game.blueTeam;
                        var receiver = team.getPlayers()[firstRole];
                        game.ball.x = receiver.hitBox.getCenterX();
                        game.ball.y = receiver.hitBox.getCenterY();
                        new RallyContactHandler(game).collideTeam(team, red, new TeamInput());
                        if (game.getHitCount(red) != 1) throw new AssertionError("Fixture did not receive first ball");
                        RuleBasedAi ai = new RuleBasedAi(red, new AiDifficulty(skill, skill), new Random(seed));
                        boolean attacked = false, returned = false;
                        int wsStart = -1, setTick = -1, apexTick = -1;
                        attempts++;
                        for (int t = 0; t < 400; t++) {
                            var oldWs = team.wingSpiker.getAction();
                            var input = ai.update(AiObservation.capture(game, red));
                            game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                            if (oldWs != PlayerAction.RUN_APPROACH && team.wingSpiker.getAction() == PlayerAction.RUN_APPROACH) {
                                wsStart = t;
                                if (setTick >= 0 && wsStart >= setTick) secondStarts.add(wsStart - apexTick);
                            }
                            if (game.didSetterContactThisFrame() && game.getLastHitTeam() != null
                                    && game.getLastHitTeam() == red && game.getHitCount(red) == 2) {
                                setTick = t;
                                apexTick = t + Math.max(0, (int) Math.ceil(-game.ball.vy / GameConfig.GRAVITY) - 1);
                                if (!team.setter.jumping && wsStart >= 0 && wsStart < t) standingLeads.add(t - wsStart);
                            }
                            if (game.didSetterContactThisFrame() && team.setter.jumping) jumpSets++;
                            for (var p : team.getPlayers()) {
                                if (p.getAction() == PlayerAction.ATTACK_SWING && p.hasValidAttackAttemptThisFrame()) attacked = true;
                            }
                            if (attacked && (red ? game.ball.x > GameConfig.NET_X + game.ball.radius
                                    : game.ball.x < GameConfig.NET_X - game.ball.radius)
                                    && game.ball.y + game.ball.radius < GameConfig.NET_TOP_Y) returned = true;
                            if (returned) break;
                            if (game.isRallyOverForNetwork()) break;
                        }
                        if (attacked) attacks++;
                        if (returned) cleared++;
                        if ("後排三米線".equals(game.transientMessage)) faults++;
                    }
                }
                System.out.printf("skill=%d first=%d opportunities=%d attacks=%d netClears=%d faults=%d jumpSets=%d%n",
                        skill, firstRole, attempts, attacks, cleared, faults, jumpSets);
                System.out.println("standingNegativeLeads=" + standingLeads + " secondStartFromApex=" + secondStarts);
                check(standingLeads.stream().allMatch(t -> t >= 42 && t <= 48), "站舉負節奏的助跑開始在 Setter 觸球前 0.7～0.8 秒");
                check(secondStarts.stream().allMatch(t -> Math.abs(t) <= 3), "第二節奏在球頂點附近開始助跑");
                check(cleared >= attempts * .85 && faults == 0, "已接起第一球後，至少 85% 能實際攻擊過網且無三米犯規");
            }
        }
        System.out.println("AiTacticsTest passed");
    }

    private static void testApproachForecastMatchesMovement() {
        var wing = new model.player.Team(true).wingSpiker;
        double x = wing.x;
        var start = new TeamInput(); start.hasFirstRegularTouch = true; start.wingActionPressed = true;
        wing.update(start);
        for (int i = 0; i < 30 && !wing.jumping; i++) wing.update(new TeamInput());
        check(wing.jumping && Math.abs(wing.x - x - model.player.WingSpiker.approachDistance()) < 1e-8,
                "助跑預估與實際移動一致，不修改玩家原本的速度或幀數");
    }
    private static void testNormalServeConversion() {
        for (int skill : new int[]{61, 100}) {
            int received = 0, cleared = 0;
            for (boolean red : new boolean[]{true, false}) {
                for (int seed = 0; seed < 24; seed++) {
                    GameModel game = new GameModel();
                    game.getServeHandler().setRedServing(!red);
                    game.getServeHandler().setWaitingForServe(true);
                    ControlInput serve = new ControlInput(); serve.servePressed = true;
                    game.update(red ? new ControlInput() : serve, red ? serve : new ControlInput());
                    var ai = new RuleBasedAi(red, new AiDifficulty(skill, skill), new Random(seed));
                    boolean reception = false, attack = false, returned = false;
                    var team = red ? game.redTeam : game.blueTeam;
                    for (int t = 0; t < 450 && !game.isRallyOverForNetwork(); t++) {
                        var input = ai.update(AiObservation.capture(game, red));
                        game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                        reception |= game.isServeReceptionComplete();
                        for (var p : team.getPlayers()) attack |= p.hasValidAttackAttemptThisFrame() && p.getAction() == PlayerAction.ATTACK_SWING;
                        if (attack && (red ? game.ball.x > 612 : game.ball.x < 588)
                                && game.ball.y + game.ball.radius < GameConfig.NET_TOP_Y) { returned = true; break; }
                    }
                    if (reception) received++;
                    if (returned) cleared++;
                }
            }
            System.out.printf("normalServe skill=%d receptions=%d/48 attackNetClears=%d/48%n", skill, received, cleared);
            check(received >= 43 && cleared >= 36, "正常發球情境至少九成接發、四分之三轉成攻擊過網");
        }
    }
    private static void testJumpServeUsesReachableVariant() {
        int hits = 0, netClears = 0;
        for (int seed = 0; seed < 40; seed++) {
            GameModel game = new GameModel();
            var ai = new RuleBasedAi(true, new AiDifficulty(100, 100), new Random(seed));
            boolean hit = false;
            for (int t = 0; t < 260 && !game.isRallyOverForNetwork(); t++) {
                var before = game.getServeHandler().getState();
                game.update(ai.update(AiObservation.capture(game, true)), new ControlInput());
                if (before == model.serve.ServeState.JUMP_TOSS && game.getServeHandler().hasLaunchedServe()) {
                    hit = true; hits++;
                } else if (before == model.serve.ServeState.WAITING_FOR_SERVE && game.getServeHandler().hasLaunchedServe()) break;
                if (hit && game.ball.x > GameConfig.NET_X + GameConfig.BALL_RADIUS
                        && game.ball.y + game.ball.radius < GameConfig.NET_TOP_Y) { netClears++; break; }
            }
        }
        System.out.printf("jumpServe hits=%d netClears=%d%n", hits, netClears);
        check(hits >= 8 && netClears == hits, "跳發依手動調整後的參數選可過網球路，不亂選已知掛網變化");
    }
    private static void testReceiveCommitmentAndZone() {
        for (boolean red : new boolean[]{true, false}) {
            GameModel game = new GameModel();
            game.getServeHandler().setRedServing(!red);
            game.getServeHandler().setWaitingForServe(true);
            game.getServeHandler().setWaitingForServe(false);
            var team = red ? game.redTeam : game.blueTeam;
            game.ball.x = red ? 280 : 920; game.ball.y = 250; game.ball.vx = red ? -1 : 1; game.ball.vy = 2;
            var ai = new RuleBasedAi(red, new AiDifficulty(100, 100), new Random(14));
            boolean moving = false;
            for (int i = 0; i < 40; i++) {
                var input = ai.update(AiObservation.capture(game, red));
                game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                if (red ? input.backLeft : input.backRight) { moving = true; break; }
            }
            check(moving, "後排開始追接來球");
            game.ball.vx = 0;
            var keep = ai.update(AiObservation.capture(game, red));
            check(red ? keep.backLeft && !keep.backRight : keep.backRight && !keep.backLeft,
                    "新反彈的反應等待期間保留接球目標，不先退位");
            game = new GameModel();
            game.getServeHandler().setRedServing(!red);
            ai.reset();
            for (int i = 0; i < 90; i++) {
                var input = ai.update(AiObservation.capture(game, red));
                game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
            }
            team = red ? game.redTeam : game.blueTeam;
            double waitingCenter = team.backPlayer.x + team.backPlayer.imageWidth / 2.0;
            if (!red) waitingCenter = GameConfig.SCREEN_WIDTH - waitingCenter;
            double zoneMiddle = (GameConfig.COURT_LEFT_X + GameConfig.NET_X - GameConfig.THREE_METER_PX) / 2;
            check(Math.abs(waitingCenter - zoneMiddle) <= GameConfig.BACK_RUN_MAX_SPEED,
                    "等待發球時後排站在三米至九米的中間，左右鏡像");
            for (double target : new double[]{50, 500}) {
                game = new GameModel();
                game.getServeHandler().setRedServing(!red);
                game.getServeHandler().setWaitingForServe(true);
                game.getServeHandler().setWaitingForServe(false);
                team = red ? game.redTeam : game.blueTeam;
                ai.reset(); game.ball.x = red ? target : 1200 - target;
                game.ball.y = -300; game.ball.vx = 0; game.ball.vy = 0;
                boolean leftWaitingZone = false;
                for (int i = 0; i < 70 && game.getHitCount(red) == 0; i++) {
                    var input = ai.update(AiObservation.capture(game, red));
                    game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                    double center = team.backPlayer.x + team.backPlayer.imageWidth / 2.0;
                    if (!red) center = 1200 - center;
                    leftWaitingZone |= target < GameConfig.COURT_LEFT_X ? center < GameConfig.COURT_LEFT_X
                            : center > GameConfig.NET_X - GameConfig.THREE_METER_PX;
                }
                check(leftWaitingZone, "發球後後排可離開三米至九米站位區追球");
            }
        }
    }
    private static void testBackRescuesAfterEarlyWingDive() {
        for (boolean red : new boolean[]{true, false}) {
            GameModel game = new GameModel();
            game.getServeHandler().setRedServing(!red);
            game.getServeHandler().setWaitingForServe(true);
            game.getServeHandler().setWaitingForServe(false);
            var team = red ? game.redTeam : game.blueTeam;
            // WS 已撲出，剩餘滑行到不了來球；後排仍有時間前進補接。
            double backX = 420 - team.backPlayer.imageWidth / 2.0;
            team.backPlayer.x = red ? backX : GameConfig.SCREEN_WIDTH - backX - team.backPlayer.imageWidth;
            game.ball.x = red ? 500 : GameConfig.SCREEN_WIDTH - 500;
            game.ball.y = 250;
            game.ball.vx = 0;
            game.ball.vy = 0;
            game.ball.syncPreviousPosition();
            var ai = new RuleBasedAi(red, new AiDifficulty(100, 100), new Random(16));
            // 先讓 AI 對同一可見球路完成反應與 WS 分工，再改變 WS 的可達狀態。
            ControlInput reserve = null;
            for (int i = 0; i < 24; i++) reserve = ai.update(AiObservation.capture(game, red));
            check(red ? reserve.backLeft : reserve.backRight, "WS 接短球時後排先保持後方補位");
            TeamInput dive = new TeamInput();
            dive.wingActionPressed = true;
            for (int i = 0; i < 48; i++) team.wingSpiker.update(dive);
            ControlInput backup = null;
            for (int i = 0; i < 4; i++) backup = ai.update(AiObservation.capture(game, red));
            check(red ? backup.backRight : backup.backLeft, "WS 的剩餘撲球接不到時立刻解除原分工");
            boolean backReceived = false;
            for (int i = 0; i < 110 && !game.isRallyOverForNetwork(); i++) {
                var input = ai.update(AiObservation.capture(game, red));
                game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                if (game.getLastHitter(red) == team.backPlayer) { backReceived = true; break; }
            }
            check(backReceived, "WS 提早撲球接不到時，後排實際越過三米線補救：side=" + red);
        }
    }
    private static void testOpenSetAvoidsMb() {
        GameModel game = new GameModel();
        game.getServeHandler().setRedServing(false); game.getServeHandler().setWaitingForServe(true); game.getServeHandler().setWaitingForServe(false);
        game.recordRegularHit(true, game.redTeam.backPlayer);
        game.recordRegularHit(true, game.redTeam.setter);
        game.ball.x = 510; game.ball.y = 480; game.ball.vx = 0; game.ball.vy = -10;
        var ai = new RuleBasedAi(true, new AiDifficulty(100, 100), new Random(3));
        for (int t = 0; t < 100 && !game.isRallyOverForNetwork(); t++) {
            var input = ai.update(AiObservation.capture(game, true));
            if (game.getHitCount(true) > 0) check(!input.mbActionPressed, "開網球不安排超出攻擊框的 MB 起跳");
            game.update(input, new ControlInput());
        }
    }
    private static void testBackCanReplaceSetter() {
        int backSets = 0;
        for (int seed = 0; seed < 24; seed++) {
            GameModel game = new GameModel();
            game.getServeHandler().setRedServing(false); game.getServeHandler().setWaitingForServe(true); game.getServeHandler().setWaitingForServe(false);
            TeamInput dive = new TeamInput(); dive.wingActionPressed = true;
            game.redTeam.wingSpiker.update(dive);
            var setter = game.redTeam.setter;
            game.ball.x = setter.hitBox.getCenterX(); game.ball.y = setter.hitBox.getCenterY();
            new RallyContactHandler(game).collideTeam(game.redTeam, true, new TeamInput());
            var ai = new RuleBasedAi(true, new AiDifficulty(100, 100), new Random(seed));
            for (int t = 0; t < 150 && !game.isRallyOverForNetwork(); t++) {
                var input = ai.update(AiObservation.capture(game, true));
                game.update(input, new ControlInput());
                if (game.didAirSetContactThisFrame() && game.getLastHitter(true) == game.redTeam.backPlayer) { backSets++; break; }
            }
        }
        System.out.println("backReplacementAirSets=" + backSets);
        check(backSets > 0, "Setter 接第一球且 WS 無法起跳時，後排能真的走過去跳舉第二球");
    }
    private static void testDeepBallDefense() {
        double[][] shots = {
            {1100, 400, GameConfig.SERVE_JUMP_HORIZONTAL_SPEED, GameConfig.SERVE_JUMP_LONG_VELOCITY_Y},
            {1100, 440, GameConfig.SERVE_JUMP_HORIZONTAL_SPEED, GameConfig.SERVE_JUMP_LONG_VELOCITY_Y},
            {1100, 420, GameConfig.SERVE_JUMP_HORIZONTAL_SPEED, GameConfig.SERVE_JUMP_LONG_VELOCITY_Y},
            {1120, 400, GameConfig.SERVE_JUMP_HORIZONTAL_SPEED, GameConfig.SERVE_JUMP_LONG_VELOCITY_Y},
            {750, 350, GameConfig.FLAT_SPIKE_HORIZONTAL_SPEED, GameConfig.FLAT_SPIKE_VELOCITY_Y},
            {650, 300, GameConfig.SPIKE_HORIZONTAL_SPEED, GameConfig.SPIKE_VELOCITY_Y},
            {610, 400, GameConfig.LONG_LOB_SPIKE_HORIZONTAL_SPEED, GameConfig.LONG_LOB_SPIKE_VELOCITY_Y}
        };
        for (int skill : new int[]{10, 50, 75, 100}) {
            int received = 0, attempts = 0;
            int[] byShot = new int[shots.length];
            for (boolean red : new boolean[]{true, false}) {
                for (int seed = 0; seed < 12; seed++) {
                    for (int shot = 0; shot < shots.length; shot++) {
                        GameModel game = new GameModel();
                        game.getServeHandler().setRedServing(!red);
                        var ai = new RuleBasedAi(red, new AiDifficulty(skill, 50), new Random(seed));
                        for (int t = 0; t < 60; t++) {
                            var input = ai.update(AiObservation.capture(game, red));
                            game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                        }
                        game.getServeHandler().setWaitingForServe(false);
                        if (shot >= 4) {
                            var opponent = red ? game.blueTeam : game.redTeam;
                            var own = red ? game.redTeam : game.blueTeam;
                            game.recordRegularHit(red, own.setter);
                            game.recordRegularHit(!red, opponent.backPlayer);
                        }
                        double[] flight = shots[shot];
                        game.ball.x = red ? flight[0] : GameConfig.SCREEN_WIDTH - flight[0];
                        game.ball.y = flight[1];
                        game.ball.vx = red ? -flight[2] : flight[2];
                        game.ball.vy = flight[3];
                        game.ball.syncPreviousPosition();
                        attempts++;
                        for (int t = 0; t < 150 && !game.isRallyOverForNetwork(); t++) {
                            var input = ai.update(AiObservation.capture(game, red));
                            game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                            if (game.getHitCount(red) > 0) { received++; byShot[shot]++; break; }
                        }
                    }
                }
            }
            System.out.printf("deepDefense skill=%d received=%d/%d byShot=%s%n",
                    skill, received, attempts, java.util.Arrays.toString(byShot));
            if (skill >= 50) {
                for (int count : byShot) {
                    check(count >= (skill >= 75 ? 21 : 18),
                            "普通以上需能穩定接起各類可達後場來球：skill=" + skill);
                }
            }
        }
    }

    private static void testRetreatDuringOpponentOrganization() {
        for (boolean red : new boolean[]{true, false}) {
            for (int skill : new int[]{1, 10, 20, 21, 50, 100}) {
                GameModel game = new GameModel();
                game.getServeHandler().setRedServing(!red);
                game.getServeHandler().setWaitingForServe(false);
                var team = red ? game.redTeam : game.blueTeam;
                var opponent = red ? game.blueTeam : game.redTeam;
                game.recordRegularHit(red, team.setter);
                game.recordRegularHit(!red, opponent.backPlayer);
                double x = 500 - team.backPlayer.imageWidth / 2.0;
                team.backPlayer.x = red ? x : GameConfig.SCREEN_WIDTH - x - team.backPlayer.imageWidth;
                game.ball.x = red ? 900 : 300;
                game.ball.y = 100;
                game.ball.vx = 0;
                game.ball.vy = 0;
                var ai = new RuleBasedAi(red, new AiDifficulty(skill, 50), new Random(15));
                for (int t = 0; t < 40; t++) {
                    var input = ai.update(AiObservation.capture(game, red));
                    game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                }
                double center = team.backPlayer.x + team.backPlayer.imageWidth / 2.0;
                if (!red) center = GameConfig.SCREEN_WIDTH - center;
                check(Math.abs(center
                        - (GameConfig.COURT_LEFT_X + GameConfig.NET_X - GameConfig.THREE_METER_PX) / 2)
                        < (GameConfig.NET_X - GameConfig.THREE_METER_PX - GameConfig.COURT_LEFT_X) / 4,
                        "對手組織進攻時，所有難度後排主動退回三米至九米間：skill=" + skill);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void testBasicSecondTouchWithoutAirSet() {
        for (int skill : new int[]{40, 60}) {
            for (boolean red : new boolean[]{true, false}) {
                int secondTouches = 0;
                for (int seed = 0; seed < 12; seed++) {
                    GameModel game = new GameModel();
                    game.getServeHandler().setRedServing(!red);
                    game.getServeHandler().setWaitingForServe(true);
                    game.getServeHandler().setWaitingForServe(false);
                    var team = red ? game.redTeam : game.blueTeam;
                    game.ball.x = team.setter.hitBox.getCenterX();
                    game.ball.y = team.setter.hitBox.getCenterY();
                    new RallyContactHandler(game).collideTeam(team, red, new TeamInput());
                    check(game.getHitCount(red) == 1, "Setter 實際接起第一球");
                    var ai = new RuleBasedAi(red, new AiDifficulty(skill, skill), new Random(seed));
                    for (int t = 0; t < 350 && !game.isRallyOverForNetwork(); t++) {
                        var input = ai.update(AiObservation.capture(game, red));
                        game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                        check(!game.didAirSetContactThisFrame(), "60 以下不能以空中舉球作為備援");
                        if (game.getHitCount(red) >= 2) { secondTouches++; break; }
                    }
                }
                check(secondTouches > 0, "未解鎖假扣真舉也能用既有操作處理 Setter 第一球之後的第二球");
            }
        }
    }

    private static void testOwnAttackCover() {
        int ordinary = 0, expert = 0;
        for (int defense : new int[]{10, 40, 50, 100}) {
            int advanced = 0;
            for (boolean red : new boolean[]{true, false}) {
                for (int seed = 0; seed < 40; seed++) {
                    GameModel game = new GameModel();
                    game.getServeHandler().setRedServing(!red);
                    game.getServeHandler().setWaitingForServe(false);
                    var team = red ? game.redTeam : game.blueTeam;
                    double x = GameConfig.COURT_LEFT_X - team.backPlayer.imageWidth / 2.0;
                    team.backPlayer.x = red ? x : GameConfig.SCREEN_WIDTH - x - team.backPlayer.imageWidth;
                    game.recordRegularHit(red, team.wingSpiker);
                    var ai = new RuleBasedAi(red, new AiDifficulty(defense, 1), new Random(seed));
                    ai.update(AiObservation.capture(game, red));
                    game.recordRegularHit(red, team.setter);
                    game.ball.x = red ? GameConfig.RED_QUICK_SET_APEX_X : GameConfig.BLUE_QUICK_SET_APEX_X;
                    game.ball.y = GameConfig.QUICK_SET_APEX_Y;
                    game.ball.vx = 0;
                    game.ball.vy = 0;
                    game.ball.syncPreviousPosition();
                    for (int t = 0; t < 38 && !game.isRallyOverForNetwork(); t++) {
                        var input = ai.update(AiObservation.capture(game, red));
                        game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                    }
                    double center = team.backPlayer.x + team.backPlayer.imageWidth / 2.0;
                    if (!red) center = GameConfig.SCREEN_WIDTH - center;
                    if (center > GameConfig.COURT_LEFT_X + GameConfig.BACK_RUN_START_SPEED) advanced++;
                    check(center < GameConfig.NET_X - GameConfig.THREE_METER_PX,
                            "未參與攻擊的後排補位仍留在後場");
                }
            }
            System.out.printf("ownAttackCover defense=%d advanced=%d/80%n", defense, advanced);
            if (defense <= 40) check(advanced == 0, "入門與簡單不加入提前補位");
            if (defense == 50) ordinary = advanced;
            if (defense == 100) expert = advanced;
        }
        check(ordinary > 0 && expert > ordinary && expert < 80,
                "普通開始偶爾補位，專家更頻繁但不是每次執行");
    }

    private static void testMbLobPreference() {
        int hits = 0, lobs = 0;
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
                var ai = new RuleBasedAi(red, new AiDifficulty(100, 100), new Random(seed));
                for (int t = 0; t < 280 && !game.isRallyOverForNetwork(); t++) {
                    var input = ai.update(AiObservation.capture(game, red));
                    game.update(red ? input : new ControlInput(), red ? new ControlInput() : input);
                    if (team.quickAttacker.getAction() == PlayerAction.ATTACK_SWING
                            && team.quickAttacker.hasValidAttackAttemptThisFrame()) {
                        hits++;
                        if (input.spikeLob) lobs++;
                        break;
                    }
                }
            }
        }
        System.out.printf("mbQuickAttack hits=%d lobs=%d%n", hits, lobs);
        check(hits >= 10 && lobs <= Math.max(1, hits / 5), "MB 可達快攻以扣球為主，吊球僅偶爾使用");
    }
}
