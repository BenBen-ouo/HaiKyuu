import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import model.GameConfig;
import model.GameModel;
import model.TeamInput;
import model.player.AttackHitBox;
import model.player.HitBox;
import model.player.Player;
import model.player.PlayerAction;
import model.player.QuickAttacker;
import model.player.Team;
import model.rally.RallyContactHandler;
import model.serve.ServeState;
import network.Packet;
import network.UdpCodec;
import view.GameRenderer;

/** 以純 Java 執行主要回合規則與同步狀態的回歸檢查。 */
public class GameplayFlowTest {
    public static void main(String[] args) throws Exception {
        testBackPlayerDrawnAboveAllOtherPlayers();
        testInitialHitBoxMirrors();
        testRemovedShortFlatCombination();
        testDiveSelectsSlowFloorBounceSpin();
        testServeReceptionAndMbChoice();
        testAttackNeedsFreshPressWithBallInHitBox();
        testAttackPressBeforeBallMovesIntoHitBox();
        testSetterQuickSetApex();
        testServeFaults();
        testBackRowAttackOnThreeMeterLine();
        testNetworkServeKeyMustBeReleasedBeforeDive();
        testNetCollisionAfterPoint();
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
        check(red.wingSpiker.hitBox.offsetX == 40 && blue.wingSpiker.hitBox.offsetX == 35,
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

    private static void testAttackNeedsFreshPressWithBallInHitBox() {
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
            check(attacker.getAction() == PlayerAction.ATTACK_SWING,
                    roles[role] + "：球未進框時重新按鍵仍播放空揮");
            placeBallInAttackHitBox(early, attacker);
            contacts.collideTeam(early.redTeam, true, pressed);
            check(early.getHitCount(true) == 1 && early.ball.vx == 0,
                    roles[role] + "：按下後同幀球才進框也不補算命中");
            attacker.update(pressed);
            attacker.captureAttackAttemptBallOverlap(early.ball);
            contacts.collideTeam(early.redTeam, true, pressed);
            check(early.getHitCount(true) == 1 && early.ball.vx == 0,
                    roles[role] + "：空揮後持續按住，球進框也不能補算命中");

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
                    roles[role] + "：球在框內重新按鍵才完成攻擊");
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
        check(attacker.getAction() == PlayerAction.ATTACK_SWING && model.getHitCount(true) == 1,
                "按鍵時球在框外，即使本幀球移進框仍只空揮");
    }

    private static Player prepareAttackReady(GameModel model, int role, TeamInput pressed) {
        model.recordRegularHit(false, model.blueTeam.backPlayer);
        model.recordRegularHit(true, model.redTeam.setter);
        pressed.hasFirstRegularTouch = true;
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
