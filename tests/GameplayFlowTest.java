import model.GameConfig;
import model.GameModel;
import model.TeamInput;
import model.player.AttackHitBox;
import model.player.HitBox;
import model.player.PlayerAction;
import model.player.QuickAttacker;
import model.player.Team;
import model.rally.RallyContactHandler;
import model.serve.ServeState;
import network.Packet;
import network.UdpCodec;

/** 以純 Java 執行主要回合規則與同步狀態的回歸檢查。 */
public class GameplayFlowTest {
    public static void main(String[] args) throws Exception {
        testInitialHitBoxMirrors();
        testRemovedShortFlatCombination();
        testDiveSelectsSlowFloorBounceSpin();
        testServeReceptionAndMbChoice();
        testServeFaults();
        testNetCollisionAfterPoint();
        testScorePhasesAndReleaseGate();
        testFinalPointStopsBeforeNextServe();
        System.out.println("GameplayFlowTest passed");
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
        check(red.wingSpiker.hitBox.offsetX == 45 && blue.wingSpiker.hitBox.offsetX == 35,
                "WS 一般框保留原本位置");
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

    private static void testServeFaults() {
        GameModel attackModel = new GameModel();
        QuickAttacker attacker = attackModel.redTeam.quickAttacker;
        attacker.startAttackSwingAnimation();
        attacker.jumping = true;
        attackModel.ball.x = attacker.attackHitBox.getCenterX();
        attackModel.ball.y = attacker.attackHitBox.getCenterY();
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
        clientModel.updateForNetworkPrediction(new TeamInput(), new TeamInput());
        check(clientModel.ball.vx < 0 && clientModel.didBallHitNetThisFrame(),
                "Client 等待得分結果期間仍會撞網反彈");

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
        networkModel.updateForNetworkPrediction(networkAction, new TeamInput());
        check(networkModel.redTeam.quickAttacker.getAction() == PlayerAction.BLOCK,
                "網路 Client 在得分後前 60 幀仍能操作角色");
        Packet.CompactState.from(model).applyTo(networkModel);
        networkModel.updateForNetworkPrediction(networkAction, new TeamInput());
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
        networkCopy.updateForNetworkPrediction(new TeamInput(), new TeamInput());
        check(networkCopy.matchOver && networkCopy.ball.x != networkBallX,
                "網路 Client 收到賽末結果後仍更新球與角色");

        model.restart();
        check(!model.matchOver && model.redScore == 0
                        && model.getServeHandler().getState() == ServeState.WAITING_FOR_SERVE,
                "按 R 重新開始後恢復等待發球");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
