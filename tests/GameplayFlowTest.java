import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;
import controller.KeyboardController;
import model.GameConfig;
import model.GameModel;
import model.TeamInput;
import model.ball.Ball;
import model.ball.NetHitBox;
import model.player.AttackHitBox;
import model.player.BackPlayer;
import model.player.HitBox;
import model.player.Player;
import model.player.PlayerAction;
import model.player.QuickAttacker;
import model.player.Team;
import model.rally.RallyContactHandler;
import model.rally.ScoringLogic;
import model.serve.ServeState;
import network.Packet;
import network.UdpCodec;
import view.CourtRenderer;
import view.GameRenderer;

/** 以純 Java 執行主要回合規則與同步狀態的回歸檢查。 */
public class GameplayFlowTest {
    public static void main(String[] args) throws Exception {
        testBackPlayerDrawnAboveAllOtherPlayers();
        testInitialHitBoxMirrors();
        testEndLineBallContact();
        testRemovedShortFlatCombination();
        testDiveSelectsSlowFloorBounceSpin();
        testServeReceptionAndMbChoice();
        testWingDiveBeforeFirstReception();
        testBlueControlsRetainNumpadMappings();
        testBlueDirectionMirrors();
        testAirSetRequiresBothDirectionKeys();
        testBlockOnlyOnceAndSetterThirdTouch();
        testAttackCanHoldSecondPressUntilBallArrives();
        testAttackPressBeforeBallMovesIntoHitBox();
        testAirSetAfterTakeoff();
        testWingAirSetMirrors();
        testSetterQuickSetApex();
        testServeFaults();
        testServeLandingAndServingTeamContacts();
        testBackRowAttackOnThreeMeterLine();
        testBackRowAirSetIgnoresThreeMeterLine();
        testBackJumpSpeedChosenAtTakeoff();
        testJumpServeFlow();
        testServePlayerCenters();
        testNetworkServeKeyMustBeReleasedBeforeDive();
        testNetCollisionAfterPoint();
        testNetRoundedTopCollision();
        testClientPredictionDoesNotResolveCollisions();
        testScorePhasesAndReleaseGate();
        testPracticeMode();
        testFinalPointStopsBeforeNextServe();
        System.out.println("GameplayFlowTest passed");
    }

    private static void testBackPlayerDrawnAboveAllOtherPlayers() {
        GameModel model = new GameModel();
        for (Player player : model.redTeam.getPlayers()) {
            player.x = -500;
            player.assetName = "missing-red-layer-test.png";
        }
        for (Player player : model.blueTeam.getPlayers()) {
            player.x = -500;
            player.assetName = "missing-blue-layer-test.png";
        }

        model.redTeam.backPlayer.x = 200;
        model.redTeam.backPlayer.y = 200;
        model.blueTeam.wingSpiker.x = 200;
        model.blueTeam.wingSpiker.y = 200;
        checkRenderedPlayerColor(model, new Color(220, 90, 90), "紅隊後排應蓋住藍隊 WS");

        model.redTeam.backPlayer.x = -500;
        model.blueTeam.wingSpiker.x = -500;
        model.redTeam.setter.x = 200;
        model.redTeam.setter.y = 200;
        model.blueTeam.backPlayer.x = 200;
        model.blueTeam.backPlayer.y = 200;
        checkRenderedPlayerColor(model, new Color(80, 125, 220), "藍隊後排應蓋住紅隊 S");
    }

    private static void checkRenderedPlayerColor(GameModel model, Color expected, String message) {
        BufferedImage canvas = new BufferedImage(
                GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            new GameRenderer().render(graphics, model);
        } finally {
            graphics.dispose();
        }
        check(canvas.getRGB(250, 250) == expected.getRGB(), message);
    }

    private static void testDiveSelectsSlowFloorBounceSpin() {
        GameModel model = new GameModel();
        model.redTeam.backPlayer.diving = true;
        model.ball.x = model.redTeam.backPlayer.hitBox.getCenterX();
        model.ball.y = model.redTeam.backPlayer.hitBox.getCenterY();
        model.ball.useFastFloorBounceSpin();

        new RallyContactHandler(model).collideTeam(model.redTeam, true, new TeamInput());

        check(!model.ball.usesFastFloorBounceSpin(), "撲球接球選擇慢速落地旋轉");
    }

    private static void testInitialHitBoxMirrors() {
        Team red = new Team(true);
        Team blue = new Team(false);

        checkMirroredHitBox(red.backPlayer.hitBox, blue.backPlayer.hitBox, "後排一般框");
        checkMirroredHitBox(red.setter.hitBox, blue.setter.hitBox, "舉球員一般框");
        checkMirroredHitBox(red.wingSpiker.hitBox, blue.wingSpiker.hitBox, "WS 一般框");
        checkMirroredHitBox(red.quickAttacker.blockHitBox,
                blue.quickAttacker.blockHitBox, "MB 攔網框");

        checkMirroredAttackHitBox(red.backPlayer.attackHitBox,
                blue.backPlayer.attackHitBox, "後排攻擊框");
        checkMirroredAttackHitBox(red.quickAttacker.attackHitBox,
                blue.quickAttacker.attackHitBox, "MB 攻擊框");
        checkMirroredAttackHitBox(red.wingSpiker.attackHitBox,
                blue.wingSpiker.attackHitBox, "WS 攻擊框");
        check(red.wingSpiker.hitBox.offsetX == 30 && blue.wingSpiker.hitBox.offsetX == 45,
                "WS 一般框採目前紅藍偏移設定");
    }

    private static void checkMirroredHitBox(HitBox red, HitBox blue, String name) {
        check(red.offsetX + red.width + blue.offsetX == GameConfig.PLAYER_IMAGE_WIDTH
                        && red.offsetY == blue.offsetY
                        && red.width == blue.width && red.height == blue.height
                        && red.arcWidth == blue.arcWidth && red.arcHeight == blue.arcHeight
                        && red.rotationDegrees == -blue.rotationDegrees,
                name + "左右幾何鏡像");
    }

    private static void checkMirroredAttackHitBox(AttackHitBox red, AttackHitBox blue, String name) {
        check(red.offsetX + red.width + blue.offsetX == GameConfig.PLAYER_IMAGE_WIDTH
                        && red.offsetY == blue.offsetY
                        && red.width == blue.width && red.height == blue.height,
                name + "左右幾何鏡像");
    }

    private static void testRemovedShortFlatCombination() {
        GameModel model = new GameModel();
        model.recordRegularHit(false, model.blueTeam.backPlayer);
        QuickAttacker attacker = model.redTeam.quickAttacker;
        attacker.startAttackSwingAnimation();
        attacker.jumping = true;
        model.ball.x = attacker.attackHitBox.getCenterX();
        model.ball.y = attacker.attackHitBox.getCenterY();
        attacker.captureAttackAttemptBallOverlap(model.ball);

        TeamInput input = new TeamInput();
        input.quickAttack = true;
        input.spikeShort = true;
        input.spikeFlat = true;
        new RallyContactHandler(model).collideTeam(model.redTeam, true, input);

        check(model.ball.vx == GameConfig.FLAT_SPIKE_SPEED_X
                        && model.ball.vy == GameConfig.FLAT_SPIKE_SPEED_Y,
                "同時按短球與平打不再使用專屬球速，依平打處理");
    }

    private static void testServeReceptionAndMbChoice() throws Exception {
        GameModel model = new GameModel();
        TeamInput serve = new TeamInput();
        serve.servePressed = true;
        model.update(serve, new TeamInput());
        check(!model.isServeReceptionComplete(), "發球本身不算接發第一球");
        check(model.getHitCount(true) == 0 && model.getHitCount(false) == 0, "發球不計次");

        model.recordRegularHit(false, model.blueTeam.backPlayer);
        check(model.isServeReceptionComplete(), "接發方一般碰撞完成第一球");
        check(model.getHitCount(false) == 1, "第一次一般碰撞計為第一球");
        model.resetCounters();
        check(model.isServeReceptionComplete(), "球過網歸零不應重啟發球犯規判定");

        QuickAttacker blocker = new QuickAttacker("player 1 MB.png", 100, 100, true);
        TeamInput firstTouchPending = new TeamInput();
        firstTouchPending.quickAttack = true;
        blocker.update(firstTouchPending);
        check(blocker.getAction() == PlayerAction.BLOCK, "本次球權尚無第一球時 MB 攔網");
        check(blocker.vy == GameConfig.QUICK_ATTACKER_JUMP_SPEED + GameConfig.GRAVITY,
                "MB 攔網使用獨立起跳速度");

        QuickAttacker attacker = new QuickAttacker("player 1 MB.png", 100, 100, true);
        TeamInput firstTouchCompleted = new TeamInput();
        firstTouchCompleted.quickAttack = true;
        firstTouchCompleted.hasFirstRegularTouch = true;
        attacker.update(firstTouchCompleted);
        check(attacker.getAction() == PlayerAction.ATTACK_READY, "本次球權第一球後 MB 準備攻擊");
        check(attacker.vy == GameConfig.QUICK_ATTACKER_JUMP_SPEED + GameConfig.GRAVITY,
                "MB 攻擊使用相同的獨立起跳速度");

        TeamInput setterInput = new TeamInput();
        setterInput.setterJump = true;
        model.redTeam.setter.update(setterInput);
        check(model.redTeam.setter.vy == GameConfig.SETTER_JUMP_SPEED + GameConfig.GRAVITY,
                "Setter 使用自己的起跳速度");

        TeamInput wingInput = new TeamInput();
        wingInput.wingAttack = true;
        wingInput.ballOnOwnSide = false;
        wingInput.hasFirstRegularTouch = true;
        model.blueTeam.wingSpiker.update(wingInput);
        check(model.blueTeam.wingSpiker.getAction() == PlayerAction.RUN_APPROACH,
                "接發方 WS 在等待發球時也可助跑");
        for (int frame = 0; frame < 30
                && model.blueTeam.wingSpiker.getAction() == PlayerAction.RUN_APPROACH; frame++) {
            model.blueTeam.wingSpiker.update(wingInput);
        }
        check(model.blueTeam.wingSpiker.getAction() == PlayerAction.ATTACK_READY
                        && model.blueTeam.wingSpiker.vy == GameConfig.WING_SPIKER_JUMP_SPEED,
                "WS 助跑結束後使用自己的起跳速度");

        byte[] snapshot = UdpCodec.welcome(1, 2, true, 3, Packet.CompactState.from(model));
        UdpCodec.Welcome received = (UdpCodec.Welcome) UdpCodec.decode(snapshot, snapshot.length);
        GameModel copy = new GameModel();
        received.state.applyTo(copy);
        check(copy.isServeReceptionComplete(), "網路快照保留已接起發球狀態");
    }

    private static void testSetterQuickSetApex() {
        checkQuickSetApex(true, 0);
        checkQuickSetApex(false, 0);
        checkQuickSetApex(true, 80);
        checkQuickSetApex(false, 80);

        for (int previousTouches : new int[]{0, 2}) {
            GameModel defaultModel = prepareSetterTouch(true, previousTouches, 0);
            GameModel shortPressedModel = prepareSetterTouch(true, previousTouches, 0);
            new RallyContactHandler(defaultModel).collideTeam(
                    defaultModel.redTeam, true, new TeamInput());
            TeamInput shortPressed = new TeamInput();
            shortPressed.spikeShort = true;
            new RallyContactHandler(shortPressedModel).collideTeam(
                    shortPressedModel.redTeam, true, shortPressed);

            check(Math.abs(defaultModel.ball.vx - shortPressedModel.ball.vx) < 1e-9
                            && Math.abs(defaultModel.ball.vy - shortPressedModel.ball.vy) < 1e-9,
                    "Setter 第一或第三球按 S 仍走原本球路");
        }

        GameModel regularSet = prepareSetterTouch(true, 1, 0);
        new RallyContactHandler(regularSet).collideTeam(
                regularSet.redTeam, true, new TeamInput());
        double highestY = Double.POSITIVE_INFINITY;
        for (int frame = 0; frame < 80; frame++) {
            regularSet.ball.update();
            highestY = Math.min(highestY, regularSet.ball.y);
        }
        check(Math.abs(highestY - GameConfig.SETTER_SET_APEX_Y) < 1e-9,
                "Setter 第二球未按 S 仍維持原本舉球高度");
    }

    private static void checkQuickSetApex(boolean redSide, double setterRise) {
        GameModel model = prepareSetterTouch(redSide, 1, setterRise);
        TeamInput input = new TeamInput();
        input.spikeShort = true;
        new RallyContactHandler(model).collideTeam(
                redSide ? model.redTeam : model.blueTeam, redSide, input);

        double targetX = redSide
                ? GameConfig.RED_QUICK_SET_APEX_X
                : GameConfig.BLUE_QUICK_SET_APEX_X;
        double targetY = GameConfig.QUICK_SET_APEX_Y;
        double highestY = Double.POSITIVE_INFINITY;
        boolean passedTarget = false;
        for (int frame = 0; frame < 80; frame++) {
            model.ball.update();
            highestY = Math.min(highestY, model.ball.y);
            if (Math.abs(model.ball.x - targetX) < 1e-9
                    && Math.abs(model.ball.y - targetY) < 1e-9) {
                passedTarget = true;
            }
        }
        check(passedTarget && Math.abs(highestY - targetY) < 1e-9,
                "紅藍快攻舉球每次均在最高點通過指定球心座標");
        check(model.getHitCount(redSide) == 2, "快攻舉球仍計為 Setter 的第二球");
    }

    private static GameModel prepareSetterTouch(boolean redSide, int previousTouches,
                                                double setterRise) {
        GameModel model = new GameModel();
        model.recordRegularHit(false, model.blueTeam.backPlayer);
        model.resetCounters();
        Team team = redSide ? model.redTeam : model.blueTeam;
        for (int touch = 0; touch < previousTouches; touch++) {
            model.recordHit(redSide, team.backPlayer);
        }
        team.setter.y -= setterRise;
        model.ball.x = team.setter.hitBox.getCenterX();
        model.ball.y = team.setter.hitBox.getY() - model.ball.radius / 2;
        return model;
    }

    private static void testBackRowAttackOnThreeMeterLine() {
        double redLine = GameConfig.NET_X - GameConfig.THREE_METER_PX;
        double blueLine = GameConfig.NET_X + GameConfig.THREE_METER_PX;
        checkBackRowAttackFault(true, redLine, true);
        checkBackRowAttackFault(false, blueLine, true);
        checkBackRowAttackFault(true, Math.nextDown(redLine), false);
        checkBackRowAttackFault(false, Math.nextUp(blueLine), false);
    }

    private static void checkBackRowAttackFault(boolean redSide, double jumpStartX, boolean expectedFault) {
        GameModel model = new GameModel();
        model.recordRegularHit(false, model.blueTeam.setter);
        Team team = redSide ? model.redTeam : model.blueTeam;
        Player backPlayer = team.backPlayer;
        backPlayer.startAttackSwingAnimation();
        backPlayer.jumping = true;
        backPlayer.jumpStartX = jumpStartX;
        model.ball.x = backPlayer.attackHitBox.getCenterX();
        model.ball.y = backPlayer.attackHitBox.getCenterY();
        backPlayer.captureAttackAttemptBallOverlap(model.ball);

        new RallyContactHandler(model).collideTeam(team, redSide, new TeamInput());

        check((model.redScore + model.blueScore == 1) == expectedFault
                        && ("後排三米線".equals(model.transientMessage)) == expectedFault,
                (redSide ? "紅隊" : "藍隊") + "後排起跳中心在三米線上的判定");
    }

    private static void testServeFaults() {
        GameModel attackModel = new GameModel();
        QuickAttacker attacker = attackModel.redTeam.quickAttacker;
        attacker.startAttackSwingAnimation();
        attacker.jumping = true;
        attackModel.ball.x = attacker.attackHitBox.getCenterX();
        attackModel.ball.y = attacker.attackHitBox.getCenterY();
        attacker.captureAttackAttemptBallOverlap(attackModel.ball);
        TeamInput attackInput = new TeamInput();
        attackInput.quickAttack = true;
        new RallyContactHandler(attackModel).collideTeam(attackModel.redTeam, true, attackInput);
        check(attackModel.blueScore == 1 && "發球犯規".equals(attackModel.transientMessage),
                "接發前攻擊框碰球由攻擊方犯規");
        check(attackModel.getHitCount(true) == 0, "違規攻擊不計為一般觸球");

        GameModel receivingAttackModel = new GameModel();
        QuickAttacker receivingAttacker = receivingAttackModel.blueTeam.quickAttacker;
        receivingAttacker.startAttackSwingAnimation();
        receivingAttacker.jumping = true;
        receivingAttackModel.ball.x = receivingAttacker.attackHitBox.getCenterX();
        receivingAttackModel.ball.y = receivingAttacker.attackHitBox.getCenterY();
        receivingAttacker.captureAttackAttemptBallOverlap(receivingAttackModel.ball);
        new RallyContactHandler(receivingAttackModel).collideTeam(
                receivingAttackModel.blueTeam, false, attackInput);
        check(receivingAttackModel.redScore == 1
                        && "發球犯規".equals(receivingAttackModel.transientMessage),
                "接發前接發方攻擊框碰球也判發球犯規");

        GameModel blockModel = new GameModel();
        QuickAttacker blocker = blockModel.redTeam.quickAttacker;
        TeamInput blockInput = new TeamInput();
        blockInput.quickAttack = true;
        for (int frame = 0; frame < 15; frame++) blocker.update(blockInput);
        check(blocker.isBlockHitBoxActive(), "攔網第二幀啟用攔網框");
        blockModel.redTeam.setter.x = -500;
        blockModel.redTeam.wingSpiker.x = -500;
        blockModel.ball.x = blocker.blockHitBox.getCenterX() - 4;
        blockModel.ball.y = blocker.blockHitBox.getCenterY();
        blockModel.ball.vx = 8;
        double beforeBlockX = blockModel.ball.x;
        new RallyContactHandler(blockModel).collideTeam(blockModel.redTeam, true, blockInput);
        check(blockModel.blueScore == 1 && "發球犯規".equals(blockModel.transientMessage),
                "接發前發球方攔球也判發球犯規");
        check(blockModel.getHitCount(true) == 0, "違規攔網不計次");
        check(blockModel.ball.x < beforeBlockX && blockModel.ball.vx < 0,
                "發球犯規的攔網接觸仍推開並反彈球");

        GameModel receivingBlockModel = new GameModel();
        QuickAttacker receivingBlocker = receivingBlockModel.blueTeam.quickAttacker;
        for (int frame = 0; frame < 15; frame++) receivingBlocker.update(blockInput);
        receivingBlockModel.blueTeam.setter.x = 1500;
        receivingBlockModel.blueTeam.wingSpiker.x = 1500;
        receivingBlockModel.ball.x = receivingBlocker.blockHitBox.getCenterX();
        receivingBlockModel.ball.y = receivingBlocker.blockHitBox.getCenterY();
        new RallyContactHandler(receivingBlockModel).collideTeam(
                receivingBlockModel.blueTeam, false, blockInput);
        check(receivingBlockModel.redScore == 1 && "發球犯規".equals(receivingBlockModel.transientMessage),
                "接發前接發方攔球也判發球犯規");

        GameModel legalBlockModel = new GameModel();
        QuickAttacker legalBlocker = legalBlockModel.blueTeam.quickAttacker;
        for (int frame = 0; frame < 15; frame++) legalBlocker.update(blockInput);
        legalBlockModel.recordRegularHit(false, legalBlockModel.blueTeam.backPlayer);
        legalBlockModel.blueTeam.setter.x = 1500;
        legalBlockModel.blueTeam.wingSpiker.x = 1500;
        legalBlockModel.ball.x = legalBlocker.blockHitBox.getCenterX();
        legalBlockModel.ball.y = legalBlocker.blockHitBox.getCenterY();
        new RallyContactHandler(legalBlockModel).collideTeam(
                legalBlockModel.blueTeam, false, blockInput);
        check(legalBlockModel.redScore == 0 && legalBlockModel.blueScore == 0,
                "第一次正常接發後，既有攔網動作不會判發球犯規");
        check(legalBlockModel.getHitCount(false) == 1, "攔網接觸完全不計次");
    }

    private static void testServeLandingAndServingTeamContacts() {
        for (boolean redSide : new boolean[]{true, false}) {
            double ownCourtX = redSide ? GameConfig.COURT_LEFT_X + 40
                    : GameConfig.COURT_RIGHT_X - 40;
            double opponentCourtX = redSide ? GameConfig.COURT_RIGHT_X - 40
                    : GameConfig.COURT_LEFT_X + 40;
            for (double landingX : new double[]{ownCourtX, opponentCourtX,
                    GameConfig.COURT_LEFT_X - 20, GameConfig.COURT_RIGHT_X + 20}) {
                GameModel model = launchedNormalServeModel(redSide);
                model.ball.x = landingX;
                model.ball.y = GameConfig.FLOOR_Y - model.ball.radius - 1;
                model.ball.vx = 0;
                model.ball.vy = 2;
                updateOnlySide(model, redSide, new TeamInput());
                String expected = landingX == ownCourtX ? "發球犯規"
                        : landingX == opponentCourtX ? "IN" : "OUT";
                check(expected.equals(model.transientMessage)
                                && (redSide ? model.blueScore : model.redScore)
                                        == (landingX == opponentCourtX ? 0 : 1),
                        "一般發球落地依本場、對場與界外分類: red=" + redSide
                                + " x=" + landingX);
            }

            double leftTouch = GameConfig.COURT_LEFT_X
                    - GameConfig.COURT_LINE_WIDTH / 2.0 - GameConfig.BALL_RADIUS;
            double rightTouch = GameConfig.COURT_RIGHT_X
                    + GameConfig.COURT_LINE_WIDTH / 2.0 + GameConfig.BALL_RADIUS;
            for (double landingX : new double[]{leftTouch, rightTouch,
                    leftTouch - 0.25, rightTouch + 0.25}) {
                GameModel model = launchedNormalServeModel(redSide);
                model.ball.x = landingX;
                model.ball.y = GameConfig.FLOOR_Y - model.ball.radius - 1;
                model.ball.vx = 0;
                model.ball.vy = 2;
                updateOnlySide(model, redSide, new TeamInput());
                boolean lineTouch = landingX == leftTouch || landingX == rightTouch;
                boolean ownLine = redSide ? landingX == leftTouch : landingX == rightTouch;
                String expected = !lineTouch ? "OUT" : ownLine ? "發球犯規" : "IN";
                check(expected.equals(model.transientMessage),
                        "發球壓到底線外緣仍算界內，完全離線才是 OUT: red=" + redSide
                                + " x=" + landingX);
            }

            for (int playerIndex : new int[]{0, 1, 3}) {
                GameModel model = launchedNormalServeModel(redSide);
                Team team = redSide ? model.redTeam : model.blueTeam;
                Player player = team.getPlayers()[playerIndex];
                for (Player other : team.getPlayers()) {
                    if (other != player) other.x = -1000;
                }
                model.ball.x = player.hitBox.getCenterX();
                model.ball.y = player.hitBox.getCenterY();
                new RallyContactHandler(model).collideTeam(team, redSide, new TeamInput());
                check("發球犯規".equals(model.transientMessage)
                                && (redSide ? model.blueScore : model.redScore) == 1
                                && model.getHitCount(redSide) == 0,
                        "接發前發球方一般碰撞立即犯規: red=" + redSide
                                + " player=" + playerIndex);
            }

            GameModel afterReception = launchedNormalServeModel(redSide);
            Team servingTeam = redSide ? afterReception.redTeam : afterReception.blueTeam;
            Team receivingTeam = redSide ? afterReception.blueTeam : afterReception.redTeam;
            afterReception.recordRegularHit(!redSide, receivingTeam.backPlayer);
            afterReception.ball.x = servingTeam.setter.hitBox.getCenterX();
            afterReception.ball.y = servingTeam.setter.hitBox.getCenterY();
            new RallyContactHandler(afterReception).collideTeam(
                    servingTeam, redSide, new TeamInput());
            check(afterReception.redScore == 0 && afterReception.blueScore == 0
                            && afterReception.getHitCount(redSide) == 1,
                    "接發方完成第一次一般接球後，發球方恢復正常觸球");
        }
    }

    private static GameModel launchedNormalServeModel(boolean redSide) {
        GameModel model = jumpServeModel(redSide);
        TeamInput input = new TeamInput();
        input.servePressed = true;
        updateOnlySide(model, redSide, input);
        check(model.getServeHandler().hasLaunchedServe() && !model.isRallyOverForNetwork()
                        && model.getServeHandler().canTeamCollideWithBall(redSide),
                "出手當幀不誤判自己碰球，後續開放發球方碰撞以裁決犯規");
        return model;
    }

    private static void testEndLineBallContact() {
        check(GameConfig.COURT_LEFT_X == 100 && GameConfig.COURT_RIGHT_X == 1100
                        && GameConfig.COURT_LINE_WIDTH == 3,
                "底線座標與 3 像素線寬固定為目前繪製規格");
        double leftTouch = GameConfig.COURT_LEFT_X
                - GameConfig.COURT_LINE_WIDTH / 2.0 - GameConfig.BALL_RADIUS;
        double rightTouch = GameConfig.COURT_RIGHT_X
                + GameConfig.COURT_LINE_WIDTH / 2.0 + GameConfig.BALL_RADIUS;
        check(ScoringLogic.isBallInCourt(leftTouch, GameConfig.BALL_RADIUS)
                        && ScoringLogic.isBallInCourt(rightTouch, GameConfig.BALL_RADIUS)
                        && !ScoringLogic.isBallInCourt(leftTouch - 0.25, GameConfig.BALL_RADIUS)
                        && !ScoringLogic.isBallInCourt(rightTouch + 0.25, GameConfig.BALL_RADIUS),
                "球體剛碰到白線外緣算界內，左右判定完全鏡像");

        BufferedImage frame = new BufferedImage(
                GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = frame.createGraphics();
        try {
            new CourtRenderer().draw(graphics, false);
        } finally {
            graphics.dispose();
        }
        for (int lineX : new int[]{100, 1100}) {
            for (int x = lineX - 1; x <= lineX + 1; x++) {
                check(frame.getRGB(x, GameConfig.FLOOR_Y_PX + 20) == Color.WHITE.getRGB(),
                        "底線 x=" + lineX + " 實際繪製為 3 像素白線");
            }
        }
    }

    private static void testNetCollisionAfterPoint() {
        GameModel scoreModel = new GameModel();
        scoreModel.awardPointWithMessage(true, "IN");
        placeBallApproachingNet(scoreModel);
        scoreModel.update(new TeamInput(), new TeamInput());
        check(scoreModel.ball.vx < 0 && scoreModel.didBallHitNetThisFrame(),
                "得分後前 60 幀仍會撞網反彈");

        GameModel clientModel = new GameModel();
        Packet.CompactState.from(scoreModel).applyTo(clientModel);
        placeBallApproachingNet(clientModel);
        clientModel.updateForNetworkPrediction(new TeamInput(), true);
        check(clientModel.ball.vx == 8 && !clientModel.didBallHitNetThisFrame(),
                "Client 不自行判定撞網，球速等待 Server 快照");

        GameModel finalModel = new GameModel();
        finalModel.redScore = 24;
        finalModel.awardPointWithMessage(true, "IN");
        placeBallApproachingNet(finalModel);
        finalModel.update(new TeamInput(), new TeamInput());
        check(finalModel.ball.vx < 0 && finalModel.didBallHitNetThisFrame(),
                "賽末結果畫面仍會撞網反彈");
    }

    private static void placeBallApproachingNet(GameModel model) {
        model.ball.x = model.netHitBox.getLeft() - model.ball.radius - 2;
        model.ball.y = model.netHitBox.getTop() + 30;
        model.ball.vx = 8;
        model.ball.vy = 0;
    }

    private static void testAttackCanHoldSecondPressUntilBallArrives() {
        String[] roles = {"後排", "MB", "WS"};
        for (int role = 0; role < roles.length; role++) {
            GameModel early = new GameModel();
            TeamInput pressed = new TeamInput();
            Player attacker = prepareAttackReady(early, role, pressed);
            RallyContactHandler contacts = new RallyContactHandler(early);
            placeBallInAttackHitBox(early, attacker);
            early.ball.vx = 0;
            attacker.captureAttackAttemptBallOverlap(early.ball);
            contacts.collideTeam(early.redTeam, true, pressed);
            check(early.getHitCount(true) == 1 && early.ball.vx == 0,
                    roles[role] + "：起跳鍵持續按住不會自動命中");

            attacker.update(new TeamInput());
            early.ball.x = -1000;
            early.ball.y = -1000;
            attacker.update(pressed);
            attacker.captureAttackAttemptBallOverlap(early.ball);
            check(attacker.getAction() == PlayerAction.ATTACK_READY,
                    roles[role] + "：第二次按鍵時球未進框，不空揮並保持準備狀態");
            placeBallInAttackHitBox(early, attacker);
            contacts.collideTeam(early.redTeam, true, pressed);
            check(early.getHitCount(true) == 1 && early.ball.vx == 0,
                    roles[role] + "：本幀角色更新後球才進框，不補算命中");
            attacker.update(pressed);
            attacker.captureAttackAttemptBallOverlap(early.ball);
            contacts.collideTeam(early.redTeam, true, pressed);
            check(early.getHitCount(true) == 2 && early.ball.vx > 0,
                    roles[role] + "：第二次按鍵持續按住，球進框後自動攻擊");

            GameModel timed = new GameModel();
            TeamInput timedPress = new TeamInput();
            Player timedAttacker = prepareAttackReady(timed, role, timedPress);
            timedAttacker.update(new TeamInput());
            placeBallInAttackHitBox(timed, timedAttacker);
            timedAttacker.update(timedPress);
            placeBallInAttackHitBox(timed, timedAttacker);
            timedAttacker.captureAttackAttemptBallOverlap(timed.ball);
            new RallyContactHandler(timed).collideTeam(timed.redTeam, true, timedPress);
            check(timed.getHitCount(true) == 2 && timed.ball.vx > 0,
                    roles[role] + "：球已在框內時第二次按鍵立即完成攻擊");

            GameModel cancelled = new GameModel();
            TeamInput cancelledPress = new TeamInput();
            Player cancelledAttacker = prepareAttackReady(cancelled, role, cancelledPress);
            cancelledAttacker.update(new TeamInput());
            cancelled.ball.x = -1000;
            cancelled.ball.y = -1000;
            cancelledAttacker.update(cancelledPress);
            cancelledAttacker.update(new TeamInput());
            placeBallInAttackHitBox(cancelled, cancelledAttacker);
            cancelledAttacker.update(new TeamInput());
            check(cancelledAttacker.getAction() == PlayerAction.ATTACK_READY
                            && !cancelledAttacker.hasValidAttackAttemptThisFrame(),
                    roles[role] + "：提早按攻擊後放開，不會繼續預約命中");
        }
    }

    private static void testAttackPressBeforeBallMovesIntoHitBox() {
        GameModel model = new GameModel();
        model.getServeHandler().setWaitingForServe(false);
        model.recordRegularHit(false, model.blueTeam.backPlayer);
        model.recordRegularHit(true, model.redTeam.setter);
        model.ball.x = 100;
        model.ball.y = 300;
        model.ball.vx = 0;
        model.ball.vy = 0;

        TeamInput press = new TeamInput();
        press.quickAttack = true;
        model.update(press, new TeamInput());
        model.update(new TeamInput(), new TeamInput());
        QuickAttacker attacker = model.redTeam.quickAttacker;
        check(attacker.getAction() == PlayerAction.ATTACK_READY, "MB 已進入攻擊準備");

        model.ball.x = attacker.attackHitBox.getX() - model.ball.radius - 5;
        model.ball.y = attacker.attackHitBox.getCenterY();
        model.ball.vx = 12;
        model.ball.vy = -GameConfig.GRAVITY;
        model.update(press, new TeamInput());
        check(attacker.getAction() == PlayerAction.ATTACK_READY && model.getHitCount(true) == 1,
                 "按鍵時球在框外，即使本幀球移進框仍不揮臂");
        model.update(press, new TeamInput());
        check(model.getHitCount(true) == 2 && model.ball.vx > 0,
                "第二次按鍵持續按住，下一幀球已進框即可攻擊");
    }

    private static void testBackRowAirSetIgnoresThreeMeterLine() throws Exception {
        double redLine = GameConfig.NET_X - GameConfig.THREE_METER_PX;
        double blueLine = GameConfig.NET_X + GameConfig.THREE_METER_PX;
        checkBackRowAirSetLegal(true, redLine);
        checkBackRowAirSetLegal(false, blueLine);
        checkBackRowAirSetLegal(true, Math.nextDown(redLine));
        checkBackRowAirSetLegal(false, Math.nextUp(blueLine));
    }

    private static void testWingAirSetMirrors() {
        for (boolean redSide : new boolean[]{true, false}) {
            GameModel model = new GameModel();
            model.getServeHandler().setRedServing(!redSide);
            model.getServeHandler().setWaitingForServe(false);
            Team team = redSide ? model.redTeam : model.blueTeam;
            model.recordRegularHit(redSide, team.setter);
            model.ball.x = GameConfig.NET_X;
            model.ball.y = 130;
            model.ball.vx = 0;
            model.ball.vy = 0;

            TeamInput approach = new TeamInput();
            approach.wingAttack = true;
            for (int frame = 0; frame < 50 && !team.wingSpiker.isAttackReady(); frame++) {
                updateOnlySide(model, redSide, approach);
            }
            check(team.wingSpiker.isAttackReady() && team.wingSpiker.jumping,
                    "紅藍 WS 都能進入空中攻擊準備狀態");
            updateOnlySide(model, redSide, new TeamInput());
            TeamInput modifier = new TeamInput();
            modifier.airSetModifier = true;
            updateOnlySide(model, redSide, modifier);
            model.ball.x = team.wingSpiker.attackHitBox.getCenterX();
            model.ball.y = team.wingSpiker.attackHitBox.getCenterY();
            model.ball.vx = 0;
            model.ball.vy = 0;
            TeamInput press = modifier.copy();
            press.wingAttack = true;
            updateOnlySide(model, redSide, press);
            check(team.wingSpiker.getAction() == PlayerAction.AIR_SETTING
                            && model.getHitCount(redSide) == 2
                            && model.didAirSetContactThisFrame()
                            && model.ball.vx == 0
                            && model.redScore == 0 && model.blueScore == 0,
                    "紅藍 WS 第二球空中舉球都由 Server 計次並送出可靠事件");
        }
    }

    private static void checkBackRowAirSetLegal(boolean redSide, double jumpStartX) throws Exception {
        GameModel model = new GameModel();
        model.getServeHandler().setRedServing(!redSide);
        model.getServeHandler().setWaitingForServe(false);
        Team team = redSide ? model.redTeam : model.blueTeam;
        model.recordRegularHit(redSide, team.setter);
        model.ball.x = GameConfig.NET_X;
        model.ball.y = 130;
        model.ball.vx = 0;
        model.ball.vy = 0;
        team.backPlayer.x = jumpStartX - team.backPlayer.imageWidth / 2.0;

        TeamInput jump = new TeamInput();
        jump.backJump = true;
        updateOnlySide(model, redSide, jump);
        check(team.backPlayer.jumping && team.backPlayer.isAttackReady(),
                "後排已起跳，準備空中舉球");
        team.backPlayer.jumpStartX = jumpStartX;
        updateOnlySide(model, redSide, new TeamInput());

        TeamInput modifier = new TeamInput();
        modifier.airSetModifier = true;
        modifier.backLeft = true;
        updateOnlySide(model, redSide, modifier);
        model.ball.x = team.backPlayer.attackHitBox.getCenterX();
        model.ball.y = team.backPlayer.attackHitBox.getCenterY();
        model.ball.vx = 0;
        model.ball.vy = 0;
        TeamInput press = modifier.copy();
        press.backJump = true;
        updateOnlySide(model, redSide, press);

        check(team.backPlayer.getAction() == PlayerAction.AIR_SETTING
                        && model.getHitCount(redSide) == 2 && model.ball.vx == 0
                        && model.didAirSetContactThisFrame(),
                "紅藍後排第二球均完成垂直空中舉球");
        check(model.redScore + model.blueScore == 0 && model.transientMessage == null,
                "後排空中舉球不論踩線與否均不判三米線違規");
        Packet.EventType eventType = Packet.EventType.AIR_SET_CONTACT;
        byte[] bytes = UdpCodec.event(7, 1, 1, eventType, 1,
                Packet.CompactState.from(model));
        UdpCodec.Event event = (UdpCodec.Event) UdpCodec.decode(bytes, bytes.length);
        GameModel client = new GameModel();
        event.state.applyToForClient(client);
        Team clientTeam = redSide ? client.redTeam : client.blueTeam;
        check(event.type == eventType
                        && client.getHitCount(redSide) == 2
                        && client.getLastHitter(redSide) == clientTeam.backPlayer
                        && clientTeam.backPlayer.getAction() == PlayerAction.AIR_SETTING
                        && clientTeam.backPlayer.jumpStartX == jumpStartX
                        && client.redScore == model.redScore
                        && client.blueScore == model.blueScore
                        && client.transientMessage == null,
                "紅藍空中舉球次數及角色狀態可由可靠事件同步");

        double highestY = model.ball.y;
        for (int frame = 0; frame < 80 && model.ball.vy < 0; frame++) {
            model.ball.update();
            highestY = Math.min(highestY, model.ball.y);
        }
        check(Math.abs(highestY - GameConfig.SETTER_SET_APEX_Y) < 1,
                "紅藍合法後排空中舉球共用同一最高點");

        double xAtSet = team.backPlayer.x;
        for (int frame = 0; frame < 120 && team.backPlayer.jumping; frame++) {
            team.backPlayer.update(new TeamInput());
        }
        check(!team.backPlayer.jumping
                        && (redSide ? team.backPlayer.x > xAtSet : team.backPlayer.x < xAtSet)
                        && team.backPlayer.getAction() == PlayerAction.IDLE
                        && team.backPlayer.assetName.equals(
                                redSide ? "player 1 back.png" : "player 2 back.png"),
                "紅藍後排空中舉球都沿本隊方向落地並恢復原圖");
    }

    private static void updateOnlySide(GameModel model, boolean redSide, TeamInput input) {
        model.update(redSide ? input : new TeamInput(), redSide ? new TeamInput() : input);
    }

    private static void testNetRoundedTopCollision() {
        NetHitBox net = new NetHitBox();
        Ball top = new Ball(net.getCenterX(), net.getTop() - GameConfig.BALL_RADIUS);
        top.vx = 0;
        top.vy = 5;
        check(top.collideWithNet(net) && top.vy < 0,
                "網子圓頂中心仍用原本反彈係數回彈");
        Ball corner = new Ball(net.getLeft() - GameConfig.BALL_RADIUS,
                net.getTop() - GameConfig.BALL_RADIUS);
        check(!net.intersectsBall(corner), "網子上方矩形直角已移除");
        check(Math.abs(net.getBottom() - GameConfig.FLOOR_Y) < 0.01,
                "網子底部仍貼地並維持直角");
    }

    private static void testAirSetAfterTakeoff() {
        for (int role : new int[]{0, 2}) {
            GameModel model = new GameModel();
            TeamInput attack = new TeamInput();
            Player player = prepareAttackReady(model, role, attack);
            attack.canBackAirSet = role == 0;
            attack.canWingAirSet = role == 2;
            attackerRelease(player, model.ball);

            TeamInput modifier = new TeamInput();
            modifier.ball = model.ball;
            modifier.airSetModifier = true;
            modifier.hasFirstRegularTouch = true;
            modifier.canBackAirSet = role == 0;
            modifier.canWingAirSet = role == 2;
            model.ball.x = -1000;
            model.ball.y = -1000;
            player.update(modifier);
            for (int frame = 0; frame < 5; frame++) {
                player.update(modifier);
            }
            check(player.getAction() == PlayerAction.ATTACK_READY,
                    "起跳後按住舉球組合鍵等待球，不提前舉球");

            TeamInput setPress = modifier.copy();
            setPress.backJump = role == 0;
            setPress.wingAttack = role == 2;
            player.update(setPress);
            check(player.getAction() == PlayerAction.ATTACK_READY
                            && !player.hasAirSetAttemptThisFrame(),
                    "球未進框時按攻擊鍵不播放舉球動畫，也不立即觸球");
            placeBallInAttackHitBox(model, player);
            player.update(setPress);
            check(player.getAction() == PlayerAction.AIR_SETTING,
                    "空中第二次攻擊鍵持續按住，球進框後切換舉球動畫");
            check(new RallyContactHandler(model).tryAirSetContact(model.redTeam, true),
                    "空中舉球於按鍵當幀完成碰球");
            check(model.redHitCount == 2 && model.ball.vx == 0,
                    "空中舉球計第二球且垂直飛行");
            double highestY = model.ball.y;
            for (int frame = 0; frame < 80 && model.ball.vy < 0; frame++) {
                model.ball.update();
                highestY = Math.min(highestY, model.ball.y);
            }
            check(Math.abs(highestY - GameConfig.SETTER_SET_APEX_Y) < 1,
                    "空中舉球沿用舉球最高點");

            double xAtSet = player.x;
            if (role == 0) {
                player.updateWhileAwaitingAuthority();
                check(player.x > xAtSet && player.getAction() == PlayerAction.AIR_SETTING,
                        "等待 Server 狀態時，後排空中舉球仍沿原方向移動");
            }
            for (int frame = 0; frame < 120 && player.jumping; frame++) {
                player.update(new TeamInput());
            }
            check(!player.jumping, "空中舉球後角色會落地");
            if (role == 0) {
                check(player.x > xAtSet + 5,
                        "後排舉球後保持原本的空中橫向軌跡直到落地");
                check(player.getAction() == PlayerAction.IDLE
                                && player.assetName.equals("player 1 back.png"),
                        "後排落地立即恢復原本狀態與圖片");
                player.update(new TeamInput());
                check(player.getAction() == PlayerAction.IDLE,
                        "後排落地後不會卡在空中舉球動作");
            }
        }

        for (int role : new int[]{0, 2}) {
            GameModel preheld = new GameModel();
            TeamInput attack = new TeamInput();
            attack.airSetModifier = true;
            Player player = prepareAttackReady(preheld, role, attack);
            TeamInput release = attack.copy();
            release.backJump = false;
            release.wingAttack = false;
            player.update(release);
            TeamInput press = attack.copy();
            press.canBackAirSet = role == 0;
            press.canWingAirSet = role == 2;
            placeBallInAttackHitBox(preheld, player);
            player.update(press);
            check(player.getAction() == PlayerAction.AIR_SETTING
                            && player.hasAirSetAttemptThisFrame(),
                    "後排與 WS 起跳前預先按住舉球組合鍵，球進框後第二次按攻擊鍵可空中舉球");
            check(new RallyContactHandler(preheld).tryAirSetContact(preheld.redTeam, true)
                            && preheld.redHitCount == 2 && preheld.ball.vx == 0,
                    "起跳前預按方向鍵的空中舉球也會於觸球當幀計第二球並垂直送出");
        }
    }

    private static void testWingDiveBeforeFirstReception() {
        for (boolean redSide : new boolean[]{true, false}) {
            GameModel model = new GameModel();
            Team team = redSide ? model.redTeam : model.blueTeam;
            TeamInput input = new TeamInput();
            input.wingAttack = true;
            team.wingSpiker.update(input);
            check(team.wingSpiker.getAction() == PlayerAction.DIVE
                            && team.wingSpiker.diving
                            && (redSide ? team.wingSpiker.vx > 0 : team.wingSpiker.vx < 0),
                    "紅藍 WS 第一球前只向網子撲球");
            check(team.wingSpiker.isDefaultHitBoxActive(),
                    "紅藍 WS 撲球期間一般碰撞框可接球");
        }
    }

    private static void testBlueControlsRetainNumpadMappings() {
        KeyboardController keyboard = new KeyboardController();
        JPanel source = new JPanel();
        for (int key : new int[]{KeyEvent.VK_NUMPAD0, KeyEvent.VK_NUMPAD4,
                KeyEvent.VK_NUMPAD5, KeyEvent.VK_NUMPAD6,
                KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT}) {
            keyboard.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0,
                    key, KeyEvent.CHAR_UNDEFINED));
        }
        TeamInput blue = keyboard.getBlueInput();
        check(blue.backJump && blue.backDive && blue.servePressed
                        && blue.wingAttack && blue.setterJump && blue.quickAttack
                        && blue.airSetModifier,
                "測試數字列按鍵不會蓋掉藍隊 NumPad 操作與雙方向鍵空中舉球");
    }

    private static void testBlueDirectionMirrors() {
        KeyboardController keyboard = new KeyboardController();
        JPanel source = new JPanel();
        keyboard.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0,
                KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
        check(keyboard.getBlueInput().spikeFlat && keyboard.getBlueInput().backLeft
                        && keyboard.getBlueInput().serveType == model.serve.ServeType.NORMAL,
                "藍隊朝網的左鍵選平打，不再誤選短發球");
        keyboard.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0,
                KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
        check(keyboard.getBlueInput().spikeFlat && keyboard.getBlueInput().spikeLob,
                "藍隊上加左選長吊球");
        keyboard.keyReleased(new KeyEvent(source, KeyEvent.KEY_RELEASED, 0, 0,
                KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
        keyboard.keyReleased(new KeyEvent(source, KeyEvent.KEY_RELEASED, 0, 0,
                KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
        keyboard.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0,
                KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED));
        check(!keyboard.getBlueInput().spikeFlat
                        && keyboard.getBlueInput().serveType == model.serve.ServeType.SHORT,
                "藍隊遠網的右鍵只選短發球，不選平打");
    }

    private static void testBackJumpSpeedChosenAtTakeoff() {
        for (boolean redSide : new boolean[]{true, false}) {
            Team team = new Team(redSide);
            double line = GameConfig.NET_X + (redSide ? -1 : 1) * GameConfig.THREE_METER_PX;
            team.backPlayer.x = (redSide ? line + 20 : line - 20) - 50;
            TeamInput jump = new TeamInput();
            jump.backJump = true;
            team.backPlayer.update(jump);
            double chosen = team.backPlayer.vx;
            check(Math.abs(chosen) < 2.8 && Math.abs(chosen) > 0,
                    "靠網側起跳依攻擊框前緣降低橫速，左右對稱");
            for (int frame = 0; frame < 34; frame++) team.backPlayer.update(new TeamInput());
            check(Math.abs(team.backPlayer.vx - chosen) < 1e-9,
                    "後排起跳後橫速不因當下位置改變");
            double front = redSide
                    ? team.backPlayer.attackHitBox.getX() + team.backPlayer.attackHitBox.width
                    : team.backPlayer.attackHitBox.getX();
            check(Math.abs(front - (team.setter.x + team.setter.imageWidth / 2.0)) < 1e-6,
                    "靠網側起跳的攻擊框前緣在最高點對齊 Setter 圖片中心");
            Team behind = new Team(redSide);
            behind.backPlayer.x = (redSide ? line - 20 : line + 20) - 50;
            behind.backPlayer.update(jump);
            check(Math.abs(behind.backPlayer.vx) == 2.8,
                    "三米線後方仍以 2.8 為橫速上限");
        }
    }

    private static void testJumpServeFlow() {
        for (boolean redSide : new boolean[]{true, false}) {
            GameModel miss = jumpServeModel(redSide);
            TeamInput toss = jumpServeInput(true);
            updateOnlySide(miss, redSide, toss);
            check(miss.getServeHandler().getState() == ServeState.JUMP_TOSS,
                    "朝網方向加發球鍵先拋球，不算已發球");
            BackPlayer server = (redSide ? miss.redTeam : miss.blueTeam).backPlayer;
            double apexCenterX = server.x + server.imageWidth / 2.0
                    + (redSide ? 1 : -1) * 2.8 * 35;
            double attackBoxOffset = server.attackHitBox.getCenterX()
                    - (server.x + server.imageWidth / 2.0);
            double tossTargetX = server.plannedAttackHitBoxCenterAtApex();
            check(Math.abs(tossTargetX - (apexCenterX + attackBoxOffset)) < 1e-9
                            && Math.abs(attackBoxOffset) > 0,
                    "跳發拋球目標包含後排攻擊框相對圖片中心的偏移");
            Ball tossFlight = new Ball(miss.ball.x, miss.ball.y);
            tossFlight.vx = miss.ball.vx;
            tossFlight.vy = miss.ball.vy;
            for (int frame = 0; frame < 140
                    && tossFlight.y + tossFlight.radius < GameConfig.FLOOR_Y; frame++) {
                tossFlight.update();
            }
            check(Math.abs(tossFlight.x - tossTargetX) <= Math.abs(tossFlight.vx) + 1e-9,
                    "紅藍跳發拋球的預定落點對準起跳最高點攻擊框中心");
            double serverStartX = (redSide ? miss.redTeam : miss.blueTeam).backPlayer.x;
            TeamInput blockedMove = new TeamInput();
            blockedMove.backLeft = true;
            blockedMove.backRight = true;
            updateOnlySide(miss, redSide, blockedMove);
            check((redSide ? miss.redTeam : miss.blueTeam).backPlayer.x == serverStartX,
                    "跳發拋球後等待起跳期間不能用方向鍵移動後排");
            double highest = miss.ball.y;
            for (int frame = 0; frame < 140 && !miss.isRallyOverForNetwork(); frame++) {
                updateOnlySide(miss, redSide, new TeamInput());
                highest = Math.min(highest, miss.ball.y);
            }
            check(Math.abs(highest - GameConfig.SETTER_SET_APEX_Y) < 1
                            && miss.isRallyOverForNetwork()
                            && "發球犯規".equals(miss.transientMessage)
                            && (redSide ? miss.blueScore == 1 : miss.redScore == 1),
                    "拋球最高點與 Setter 相同；未擊球落地判發球方犯規");

            boolean hit = false;
            int successfulDelay = -1;
            for (int delay = 15; delay <= 45 && !hit; delay++) {
                GameModel model = jumpServeModel(redSide);
                updateOnlySide(model, redSide, jumpServeInput(true));
                for (int frame = 0; frame < delay; frame++) {
                    updateOnlySide(model, redSide, new TeamInput());
                }
                TeamInput press = jumpServeInput(false);
                updateOnlySide(model, redSide, press);
                check(model.getServeHandler().getState() == ServeState.JUMP_TOSS
                                && (redSide ? model.redTeam : model.blueTeam).backPlayer.jumping,
                        "第二次按發球鍵只讓後排起跳，不發球");
                updateOnlySide(model, redSide, new TeamInput());
                updateOnlySide(model, redSide, press);
                for (int frame = 0; frame < 65 && !model.isRallyOverForNetwork(); frame++) {
                    if (model.getServeHandler().getState() != ServeState.JUMP_TOSS) {
                        hit = true;
                        break;
                    }
                    updateOnlySide(model, redSide, press);
                }
                if (hit) {
                    successfulDelay = delay;
                    check(model.ball.vx == (redSide ? 1 : -1) * GameConfig.SERVE_JUMP_VX
                                    && !model.isServeReceptionComplete(),
                            "第三次按住發球鍵，球進攻擊框後才由 Server 真正發球");
                    Packet.CompactState.from(model).applyTo(new GameModel());
                }
            }
            check(hit, "紅藍跳發球拋球與後排起跳有可命中的時機");
            for (int route = 0; route < 4; route++) {
                checkJumpServeRoute(redSide, successfulDelay, route);
            }
            for (int delay = 0; delay <= 60; delay++) {
                for (int route = 0; route < 4; route++) {
                    checkJumpServeRouteIfHittable(redSide, delay, route);
                }
            }
        }
    }

    private static void testServePlayerCenters() {
        GameModel redServe = new GameModel();
        GameModel blueServe = new GameModel();
        blueServe.getServeHandler().setRedServing(false);
        blueServe.getServeHandler().setWaitingForServe(true);
        double redCenter = redServe.redTeam.backPlayer.x
                + redServe.redTeam.backPlayer.imageWidth / 2.0;
        double blueCenter = blueServe.blueTeam.backPlayer.x
                + blueServe.blueTeam.backPlayer.imageWidth / 2.0;
        check(redCenter == -10 && blueCenter == GameConfig.SCREEN_WIDTH + 10,
                "待發球後排圖片中心在紅 -10、藍 1210，左右鏡像");
        check(GameConfig.COURT_LEFT_X - redCenter == blueCenter - GameConfig.COURT_RIGHT_X,
                "兩隊後排圖片中心距離底線相同");
        check(redServe.ball.x == GameConfig.RED_SERVE_BALL_X
                        && blueServe.ball.x == GameConfig.BLUE_SERVE_BALL_X
                        && redServe.ball.x == GameConfig.BALL_RADIUS
                        && blueServe.ball.x == GameConfig.SCREEN_WIDTH - GameConfig.BALL_RADIUS,
                "發球員往場內移後，紅藍待發球球心仍停在原本位置");
    }

    private static void checkJumpServeRoute(boolean redSide, int delay, int route) {
        check(checkJumpServeRouteIfHittable(redSide, delay, route),
                "指定起跳時機可擊中跳發球");
    }

    private static boolean checkJumpServeRouteIfHittable(boolean redSide, int delay, int route) {
        GameModel model = jumpServeModel(redSide);
        updateOnlySide(model, redSide, jumpServeInput(true));
        for (int frame = 0; frame < delay; frame++) updateOnlySide(model, redSide, new TeamInput());
        updateOnlySide(model, redSide, jumpServeInput(false));
        updateOnlySide(model, redSide, new TeamInput());
        TeamInput hit = jumpServeInput(false);
        if (route == 1) hit.spikeShort = true;
        if (route == 2) {
            if (redSide) hit.backLeft = true;
            else hit.backRight = true;
        }
        if (route == 3) {
            if (redSide) hit.backRight = true;
            else hit.backLeft = true;
        }
        for (int frame = 0; frame < 65 && model.getServeHandler().getState() == ServeState.JUMP_TOSS; frame++) {
            updateOnlySide(model, redSide, hit.copy());
        }
        if (model.getServeHandler().getState() == ServeState.JUMP_TOSS) return false;
        double expectedVx = route == 2 ? GameConfig.SERVE_JUMP_SLOW_VX : GameConfig.SERVE_JUMP_VX;
        double expectedVy = route == 1 ? GameConfig.SERVE_JUMP_SHORT_VY
                : route == 3 ? GameConfig.SERVE_JUMP_LONG_VY : GameConfig.SERVE_JUMP_VY;
        check(Math.abs(model.ball.vx) == expectedVx
                        && Math.abs(model.ball.vy - (expectedVy + GameConfig.GRAVITY)) < 1e-9,
                "跳發球路於命中時依方向鍵選擇水平或垂直初速: route=" + route
                        + " side=" + redSide + " vx=" + model.ball.vx + " vy=" + model.ball.vy);
        // 球路速度由玩家在 GameConfig 手動調整；此測試只檢查按鍵對應的出手速度，
        // 不把目前的調校值是否過網、界內當成固定玩法規則。
        if (route == 0) {
            double ownCourtX = redSide ? GameConfig.COURT_LEFT_X + 40
                    : GameConfig.COURT_RIGHT_X - 40;
            double opponentCourtX = redSide ? GameConfig.COURT_RIGHT_X - 40
                    : GameConfig.COURT_LEFT_X + 40;
            for (double landingX : new double[]{ownCourtX, opponentCourtX,
                    GameConfig.COURT_LEFT_X - 20, GameConfig.COURT_RIGHT_X + 20}) {
                GameModel landingModel = new GameModel();
                Packet.CompactState.from(model).applyTo(landingModel);
                landingModel.ball.x = landingX;
                landingModel.ball.y = GameConfig.FLOOR_Y - landingModel.ball.radius - 1;
                landingModel.ball.vx = 0;
                landingModel.ball.vy = 2;
                updateOnlySide(landingModel, redSide, new TeamInput());
                String expected = landingX == ownCourtX ? "發球犯規"
                        : landingX == opponentCourtX ? "IN" : "OUT";
                check(expected.equals(landingModel.transientMessage)
                                && (redSide ? landingModel.blueScore : landingModel.redScore)
                                        == (landingX == opponentCourtX ? 0 : 1),
                        "跳發擊出後依落點分類，不再將出界誤判成發球犯規: red="
                                + redSide + " x=" + landingX);
            }
        }
        return true;
    }

    private static GameModel jumpServeModel(boolean redSide) {
        GameModel model = new GameModel();
        model.getServeHandler().setRedServing(redSide);
        model.getServeHandler().setWaitingForServe(true);
        return model;
    }

    private static TeamInput jumpServeInput(boolean towardNet) {
        TeamInput input = new TeamInput();
        input.servePressed = true;
        input.backJump = true;
        input.backDive = true;
        input.spikeFlat = towardNet;
        return input;
    }

    private static void testAirSetRequiresBothDirectionKeys() {
        JPanel source = new JPanel();
        KeyboardController keyboard = new KeyboardController();
        keyboard.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0,
                KeyEvent.VK_A, KeyEvent.CHAR_UNDEFINED));
        keyboard.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0,
                KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
        check(!keyboard.getRedInput().airSetModifier && !keyboard.getBlueInput().airSetModifier,
                "紅藍單按往左方向鍵不觸發空中舉球修正鍵");
        keyboard.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0,
                KeyEvent.VK_D, KeyEvent.CHAR_UNDEFINED));
        keyboard.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0,
                KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED));
        check(keyboard.getRedInput().airSetModifier && keyboard.getBlueInput().airSetModifier,
                "紅隊 A+D、藍隊左+右同按才啟用空中舉球");
        keyboard.keyReleased(new KeyEvent(source, KeyEvent.KEY_RELEASED, 0, 0,
                KeyEvent.VK_A, KeyEvent.CHAR_UNDEFINED));
        keyboard.keyReleased(new KeyEvent(source, KeyEvent.KEY_RELEASED, 0, 0,
                KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
        check(!keyboard.getRedInput().airSetModifier && !keyboard.getBlueInput().airSetModifier,
                "放開任一方向鍵就取消空中舉球修正鍵");
    }

    private static void testBlockOnlyOnceAndSetterThirdTouch() {
        for (boolean redSide : new boolean[]{true, false}) {
            GameModel model = new GameModel();
            model.getServeHandler().setRedServing(!redSide);
            Team team = redSide ? model.redTeam : model.blueTeam;
            QuickAttacker blocker = team.quickAttacker;
            TeamInput block = new TeamInput();
            block.quickAttack = true;
            for (int frame = 0; frame < 15; frame++) blocker.update(block);
            model.ball.x = blocker.blockHitBox.getCenterX();
            model.ball.y = blocker.blockHitBox.getCenterY();
            double incomingVx = redSide ? -8 : 8;
            model.ball.vx = incomingVx;
            model.recordRegularHit(redSide, team.setter);
            RallyContactHandler contacts = new RallyContactHandler(model);
            contacts.collideTeam(team, redSide, block);
            check(model.hasBlocked(redSide) && model.getHitCount(redSide) == 1,
                    "紅藍首次攔網反彈但不計次");
            model.ball.x = blocker.blockHitBox.getCenterX();
            model.ball.y = blocker.blockHitBox.getCenterY();
            model.ball.vx = incomingVx;
            contacts.collideTeam(team, redSide, block);
            check(model.ball.vx == incomingVx && model.getHitCount(redSide) == 1,
                    "紅藍同隊第二次碰到攔網框直接穿過");
            model.resetTeamContacts(redSide);
            check(model.hasBlocked(redSide), "單純重算觸球次數不能重新開放攔網框");
            model.recordRegularHit(redSide, team.setter);

            model.recordRegularHit(redSide, team.backPlayer);
            model.ball.x = team.setter.hitBox.getCenterX();
            model.ball.y = team.setter.hitBox.getCenterY();
            contacts.collideTeam(team, redSide, new TeamInput());
            check(model.getHitCount(redSide) == 3 && model.getLastHitter(redSide) == team.setter,
                    "紅藍 Setter 接第一球後可再接第三球");
        }
    }

    private static void attackerRelease(Player player, model.ball.Ball ball) {
        TeamInput release = new TeamInput();
        release.ball = ball;
        release.hasFirstRegularTouch = true;
        player.update(release);
    }

    private static Player prepareAttackReady(GameModel model, int role, TeamInput pressed) {
        model.recordRegularHit(false, model.blueTeam.backPlayer);
        model.recordRegularHit(true, model.redTeam.setter);
        pressed.hasFirstRegularTouch = true;
        pressed.ball = model.ball;
        Player attacker;
        if (role == 0) {
            attacker = model.redTeam.backPlayer;
            pressed.backJump = true;
        } else if (role == 1) {
            attacker = model.redTeam.quickAttacker;
            pressed.quickAttack = true;
        } else {
            attacker = model.redTeam.wingSpiker;
            pressed.wingAttack = true;
        }
        for (int frame = 0; frame < 80 && attacker.getAction() != PlayerAction.ATTACK_READY; frame++) {
            attacker.update(pressed);
        }
        check(attacker.getAction() == PlayerAction.ATTACK_READY, "攻擊者完成助跑或起跳");
        for (Player other : model.redTeam.getPlayers()) {
            if (other != attacker) other.x = -500;
        }
        return attacker;
    }

    private static void placeBallInAttackHitBox(GameModel model, Player attacker) {
        model.ball.x = attacker.attackHitBox.getCenterX();
        model.ball.y = attacker.attackHitBox.getCenterY();
    }

    private static void testClientPredictionDoesNotResolveCollisions() {
        GameModel client = new GameModel();
        client.getServeHandler().setWaitingForServe(false);
        placeBallApproachingNet(client);
        double ballX = client.ball.x;
        client.updateForNetworkPrediction(new TeamInput(), true);
        check(client.ball.x == ballX && client.ball.vx == 8 && !client.didBallHitNetThisFrame(),
                "Client 不模擬撞網，也不改變權威球位置或速度");

        client.ball.x = client.redTeam.setter.hitBox.getCenterX();
        client.ball.y = client.redTeam.setter.hitBox.getCenterY();
        client.updateForNetworkPrediction(new TeamInput(), true);
        check(client.redHitCount == 0 && client.ball.vx == 8,
                "Client 不自行判定球員觸球");

        client.ball.y = GameConfig.FLOOR_Y - client.ball.radius + 1;
        client.updateForNetworkPrediction(new TeamInput(), true);
        TeamInput action = new TeamInput();
        action.quickAttack = true;
        client.updateForNetworkPrediction(action, true);
        check(client.redScore == 0 && client.blueScore == 0
                        && !client.isRallyOverForNetwork()
                        && client.redTeam.quickAttacker.getAction() == PlayerAction.BLOCK,
                "Client 預測到落地也不能停止本機操作或自行結束回合");
    }

    private static void testNetworkServeKeyMustBeReleasedBeforeDive() {
        GameModel server = new GameModel();
        TeamInput heldServe = new TeamInput();
        heldServe.servePressed = true;
        heldServe.backJump = true;
        heldServe.backDive = true;
        server.update(heldServe.copy(), new TeamInput());
        check(server.getServeHandler().getState() == ServeState.IN_PLAY,
                "Server 可在發球同幀直接進入 IN_PLAY");

        GameModel client = new GameModel();
        Packet.CompactState.from(server).applyToForClient(client);
        client.getServeHandler().lockNetworkPostServeBackAction();
        for (int frame = 0; frame < 5; frame++) {
            if (frame == 2) {
                Packet.CompactState.from(server).applyToForClient(client);
            }
            client.updateForNetworkPrediction(heldServe, true);
        }
        check(client.redTeam.backPlayer.getAction() != PlayerAction.DIVE,
                "Client 收到 IN_PLAY 的 SERVE 事件後，按住原發球鍵不會撲球");

        client.updateForNetworkPrediction(new TeamInput(), true);
        check(client.redTeam.backPlayer.getAction() != PlayerAction.DIVE,
                "放開發球鍵的當幀也不會撲球");
        client.updateForNetworkPrediction(heldServe, true);
        check(client.redTeam.backPlayer.getAction() == PlayerAction.DIVE,
                "發球鍵放開後重新按下，才允許後排撲球");
    }

    private static void testScorePhasesAndReleaseGate() throws Exception {
        GameModel model = new GameModel();
        double initialSetterX = new Team(true).setter.x;
        double initialQuickX = new Team(true).quickAttacker.x;
        double initialWingX = new Team(true).wingSpiker.x;
        model.awardPointWithMessage(true, "IN");
        model.redTeam.setter.x += 20;
        model.redTeam.quickAttacker.x += 20;
        model.redTeam.wingSpiker.x += 20;
        model.spikeEffect.startSpikeTrail(true);
        model.spikeEffect.addTrailPoint(1, 2);
        model.spikeEffect.spawnSmoke(3, 4);

        TeamInput dive = new TeamInput();
        dive.backJump = true;
        dive.backDive = true;
        model.update(dive, new TeamInput());
        check(model.redTeam.backPlayer.diving && !model.redTeam.backPlayer.jumping,
                "第一球未接起時死球階段的後排動作是撲球");

        for (int frame = 1; frame < 59; frame++) model.update(new TeamInput(), new TeamInput());
        check(!model.isLockedScorePhase(), "第 59 幀仍可操作");
        model.update(new TeamInput(), new TeamInput());
        check(model.isLockedScorePhase(), "第 60 幀結束後進入 30 幀鎖定");
        check(model.redTeam.backPlayer.x == GameConfig.RED_BACK_SERVE_X
                        && model.redTeam.backPlayer.y == GameConfig.RED_BACK_SERVE_Y,
                "階段切換時下一球發球後排直接站到發球位");
        check(model.blueTeam.backPlayer.x == new Team(false).backPlayer.x,
                "接發方後排照常歸位");
        check(model.redTeam.setter.x == initialSetterX
                        && model.redTeam.quickAttacker.x == initialQuickX
                        && model.redTeam.wingSpiker.x == initialWingX,
                "階段切換時其他球員也歸位");
        check(!model.redTeam.backPlayer.diving, "階段切換時取消舊動作");
        check(model.spikeEffect.getTrailPoints().isEmpty()
                        && model.spikeEffect.getSmokeParticles().isEmpty()
                        && !model.spikeEffect.isSpikeTrailActive(),
                "鎖定階段清除軌跡與煙霧");
        check(model.transientMessageTimer == 30, "得分原因剩餘 30 幀");
        double hiddenBallX = model.ball.x;
        double hiddenBallY = model.ball.y;

        byte[] snapshot = UdpCodec.welcome(1, 2, true, 3, Packet.CompactState.from(model));
        GameModel copy = new GameModel();
        ((UdpCodec.Welcome) UdpCodec.decode(snapshot, snapshot.length)).state.applyTo(copy);
        check(copy.isLockedScorePhase()
                        && copy.redTeam.backPlayer.x == GameConfig.RED_BACK_SERVE_X,
                "網路快照同步歸位與鎖定階段");

        GameModel networkModel = new GameModel();
        GameModel serverModel = new GameModel();
        serverModel.awardPointWithMessage(true, "IN");
        Packet.CompactState.from(serverModel).applyTo(networkModel);
        TeamInput networkAction = new TeamInput();
        networkAction.quickAttack = true;
        networkModel.updateForNetworkPrediction(networkAction, true);
        check(networkModel.redTeam.quickAttacker.getAction() == PlayerAction.BLOCK,
                "網路 Client 在得分後前 60 幀仍能操作角色");
        Packet.CompactState.from(model).applyTo(networkModel);
        networkModel.updateForNetworkPrediction(networkAction, true);
        check(networkModel.redTeam.quickAttacker.getAction() == PlayerAction.IDLE,
                "網路 Client 在後 30 幀禁止新動作");

        for (int frame = 0; frame < 29; frame++) {
            TeamInput held = new TeamInput();
            held.backLeft = true;
            held.servePressed = true;
            held.quickAttack = true;
            model.update(held, new TeamInput());
        }
        check(model.isLockedScorePhase()
                        && model.redTeam.backPlayer.x == GameConfig.RED_BACK_SERVE_X,
                "30 幀期間不能移動或觸發動作");
        check(model.ball.x == hiddenBallX && model.ball.y == hiddenBallY,
                "30 幀期間球不更新");
        TeamInput held = new TeamInput();
        held.servePressed = true;
        held.quickAttack = true;
        model.update(held, new TeamInput());
        check(model.getServeHandler().getState() == ServeState.WAITING_FOR_SERVE,
                "90 幀結束才進入等待發球");
        check(model.transientMessage == null, "得分原因在第 90 幀結束");

        held = new TeamInput();
        held.servePressed = true;
        held.quickAttack = true;
        model.update(held, new TeamInput());
        check(model.getServeHandler().getState() == ServeState.WAITING_FOR_SERVE,
                "鎖定期持續按住的發球鍵不自動發球");
        check(model.redTeam.quickAttacker.getAction() == PlayerAction.IDLE,
                "鎖定期持續按住的角色鍵不自動觸發");

        model.update(new TeamInput(), new TeamInput());
        TeamInput freshPress = new TeamInput();
        freshPress.servePressed = true;
        freshPress.quickAttack = true;
        model.update(freshPress, new TeamInput());
        check(model.getServeHandler().getState() != ServeState.WAITING_FOR_SERVE,
                "放開後重新按下才發球");
        check(model.redTeam.quickAttacker.getAction() == PlayerAction.BLOCK,
                "放開後重新按下才觸發 MB 動作");

        GameModel blueServing = new GameModel();
        blueServing.awardPointWithMessage(false, "OUT");
        for (int frame = 0; frame < 60; frame++) {
            blueServing.update(new TeamInput(), new TeamInput());
        }
        check(blueServing.blueTeam.backPlayer.x == GameConfig.BLUE_BACK_SERVE_X
                        && blueServing.blueTeam.backPlayer.y == GameConfig.BLUE_BACK_SERVE_Y,
                "藍隊取得發球權時也直接站到發球位");
    }

    private static void testPracticeMode() {
        GameModel model = new GameModel(true);
        check(model.isPracticeMode() && !model.getServeHandler().isRedServing()
                        && model.getServeHandler().isWaitingForServe(),
                "練習模式一開始由藍隊等待發球");
        check(model.blueTeam.backPlayer.x == GameConfig.BLUE_BACK_SERVE_X
                        && model.redTeam.backPlayer.x == new Team(true).backPlayer.x,
                "練習模式只讓藍隊後排站到發球位");

        TeamInput redServe = new TeamInput();
        redServe.servePressed = true;
        model.update(redServe, new TeamInput());
        check(model.getServeHandler().isWaitingForServe(), "紅隊不能在練習模式發球");

        TeamInput blueServe = new TeamInput();
        blueServe.servePressed = true;
        model.update(new TeamInput(), blueServe);
        check(!model.getServeHandler().isWaitingForServe() && model.ball.vx < 0,
                "藍隊仍使用一般發球流程向紅隊發球");

        for (boolean redWins : new boolean[]{true, false}) {
            model.awardPointWithMessage(redWins, redWins ? "IN" : "OUT");
            check(model.redScore == 0 && model.blueScore == 0 && !model.matchOver
                            && !model.getServeHandler().isRedServing(),
                    "練習模式不計分且得失分後都維持藍隊發球");
            check(model.transientMessageTimer == 90, "練習模式判決仍顯示 90 幀");

            for (int frame = 0; frame < 60; frame++) {
                model.update(new TeamInput(), new TeamInput());
            }
            check(model.isLockedScorePhase()
                            && model.blueTeam.backPlayer.x == GameConfig.BLUE_BACK_SERVE_X,
                    "練習模式前 60 幀結束後仍歸位並鎖定");

            for (int frame = 0; frame < 30; frame++) {
                model.update(new TeamInput(), new TeamInput());
            }
            check(model.getServeHandler().isWaitingForServe()
                            && !model.getServeHandler().isRedServing()
                            && model.transientMessage == null,
                    "練習模式後 30 幀結束才重新等待藍隊發球");
        }

        model.restart();
        check(model.isPracticeMode() && model.getServeHandler().isWaitingForServe()
                        && !model.getServeHandler().isRedServing()
                        && model.blueTeam.backPlayer.x == GameConfig.BLUE_BACK_SERVE_X
                        && model.redTeam.backPlayer.x == new Team(true).backPlayer.x,
                "練習模式按 R 重開後仍由藍隊發球");
        check(!new GameModel().isPracticeMode(), "一般單機與 Server 預設不是練習模式");
    }

    private static void testFinalPointStopsBeforeNextServe() {
        GameModel model = new GameModel();
        TeamInput serve = new TeamInput();
        serve.servePressed = true;
        model.update(serve, new TeamInput());
        model.redScore = 24;
        model.blueScore = 23;
        model.awardPointWithMessage(true, "IN");
        ServeState stateAtFinalPoint = model.getServeHandler().getState();
        double ballX = model.ball.x;
        model.spikeEffect.startSpikeTrail(true);
        model.spikeEffect.addTrailPoint(ballX, model.ball.y);
        model.spikeEffect.spawnSmoke(ballX, model.ball.y);
        int initialSmokeFrames = model.spikeEffect.getSmokeParticles().get(0).remainingFrames;
        TeamInput redAction = new TeamInput();
        redAction.quickAttack = true;
        TeamInput blueAction = new TeamInput();
        blueAction.quickAttack = true;
        model.update(redAction, blueAction);
        check(model.redTeam.quickAttacker.getAction() == PlayerAction.BLOCK
                        && model.blueTeam.quickAttacker.getAction() == PlayerAction.BLOCK,
                "賽末結果顯示後雙方仍可操作球員");
        check(model.ball.x != ballX
                        && !model.spikeEffect.getTrailPoints().isEmpty()
                        && model.spikeEffect.getSmokeParticles().get(0).remainingFrames < initialSmokeFrames,
                "賽末結果顯示後球、軌跡與煙霧仍持續更新");
        for (int frame = 0; frame < 100; frame++) model.update(new TeamInput(), new TeamInput());
        check(model.matchOver && model.getServeHandler().getState() == stateAtFinalPoint,
                "賽末得分立即顯示結果，且永不準備下一次發球");
        check(model.redScore == 25 && model.blueScore == 23,
                "賽末繼續動畫時不再判定碰撞或得分");

        GameModel networkCopy = new GameModel();
        Packet.CompactState.from(model).applyTo(networkCopy);
        double networkBallX = networkCopy.ball.x;
        networkCopy.updateForNetworkPrediction(new TeamInput(), true);
        check(networkCopy.matchOver && networkCopy.ball.x == networkBallX,
                "網路 Client 賽末只延續本機操作，不自行推進權威球");

        model.restart();
        check(!model.matchOver && model.redScore == 0
                        && model.getServeHandler().getState() == ServeState.WAITING_FOR_SERVE,
                "按 R 重新開始後恢復等待發球");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
