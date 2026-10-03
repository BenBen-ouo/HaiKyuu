package network;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import model.GameModel;
import model.TeamInput;
import model.player.PlayerAction;
import model.serve.ServeState;

/** 可用 java -cp build network.NetworkSyncTest 執行的封包與亂序測試。 */
public final class NetworkSyncTest {
    public static void main(String[] args) throws Exception {
        testWorldSnapshotRoundTripAndOrdering();
        testBlockJumpCountSnapshot();
        testRallyContactsWithoutReliableEvent();
        testReliableEventRestoresContactHistory();
        testReliableEventsStayInOrder();
        testServerKeepsUnacknowledgedEvents();
        testControlsStayInOrder();
        testMotionSnapshotKeepsAnimationAlive();
        testAuthoritativeAttackSwingSnapshot();
        testMbSnapshotReconcilesActionAndAsset();
        testAirSetInputAndSnapshotAnimation();
        testJumpServeInputAndTossEvent();
        testRemoteInterpolationStopsAfterBoundedExtrapolation();
        testSnapshotDrivenVisualEffects();
        if (args.length > 0 && "loopback".equals(args[0])) {
            testLocalLoopback();
        }
        System.out.println("NetworkSyncTest passed");
    }

    private static void testWorldSnapshotRoundTripAndOrdering() throws Exception {
        GameModel model = new GameModel();
        model.ball.x = 312.5;
        model.redTeam.setter.x = 345.5;
        model.spikeEffect.startSpikeTrail(true);
        Packet.WorldSnapshot state = Packet.WorldSnapshot.from(model, 0);
        UdpCodec.WorldSnapshotFrame first = snapshot(1, 0, state);
        check(first.snapshot.ball.x == 312.5
                        && first.snapshot.redTeam.players[1].x == 345.5
                        && first.snapshot.blueTeam.players.length == 4
                        && first.snapshot.spikeTrailActive && first.snapshot.spikeTrailRedSide,
                "即時快照包含球、雙方球員及純視覺軌跡狀態");

        WorldSnapshotInbox inbox = new WorldSnapshotInbox();
        check(inbox.offer(first, 0) == first, "初始快照可立即套用");
        UdpCodec.WorldSnapshotFrame future = snapshot(2, 1, Packet.WorldSnapshot.from(model, 1));
        UdpCodec.WorldSnapshotFrame newer = snapshot(3, 1, Packet.WorldSnapshot.from(model, 1));
        check(inbox.offer(future, 0) == future && inbox.offer(newer, 0) == newer,
                "位置快照不因可靠事件尚未抵達而停止");
        check(inbox.offer(future, 1) == null, "過期快照不倒退球員位置");
        UdpCodec.WorldSnapshotFrame oldRevision = snapshot(4, 0, Packet.WorldSnapshot.from(model, 0));
        check(inbox.offer(oldRevision, 1) == null, "舊碰撞版本不得覆蓋較新位置");
    }

    private static void testBlockJumpCountSnapshot() throws Exception {
        GameModel server = new GameModel();
        TeamInput block = new TeamInput();
        block.quickAttack = true;
        block.opponentHasFirstRegularTouch = true;
        server.redTeam.quickAttacker.update(block);
        Packet.WorldSnapshot decoded = snapshot(1, 0, Packet.WorldSnapshot.from(server, 0)).snapshot;
        GameModel client = new GameModel();
        decoded.redTeam.applyMotionTo(client.redTeam);
        check(client.redTeam.quickAttacker.getCountedBlockJumps() == 1
                        && client.redTeam.quickAttacker.hasSeenOpponentFirstTouch(),
                "攔網起跳次數與啟用狀態隨快照同步");
    }

    private static void testJumpServeInputAndTossEvent() throws Exception {
        for (boolean redSide : new boolean[]{true, false}) {
            GameModel server = new GameModel();
            server.getServeHandler().setRedServing(redSide);
            server.getServeHandler().setWaitingForServe(true);
            TeamInput local = new TeamInput();
            local.servePressed = true;
            local.backJump = true;
            local.backDive = true;
            local.backRight = true; // 兩台 Client 均用 D 朝網。
            local.spikeFlat = true;
            TeamInput decoded = Packet.decodeInput(Packet.encodeInput(local));
            TeamInput world = redSide ? decoded : decoded.mirroredHorizontally();
            server.update(redSide ? world : new TeamInput(), redSide ? new TeamInput() : world);
            check(server.getServeHandler().getState() == ServeState.JUMP_TOSS,
                    "紅藍連線輸入均可用 D+Space 開始跳發拋球");
            byte[] encoded = UdpCodec.event(7, 1, 1, Packet.EventType.JUMP_TOSS, 1,
                    Packet.CompactState.from(server));
            UdpCodec.Event event = (UdpCodec.Event) UdpCodec.decode(encoded, encoded.length);
            GameModel client = new GameModel();
            event.state.applyToForClient(client);
            check(event.type == Packet.EventType.JUMP_TOSS
                            && client.getServeHandler().getState() == ServeState.JUMP_TOSS
                            && Math.abs(client.ball.x - server.ball.x) < 1e-9
                            && Math.abs(client.ball.vy - server.ball.vy) < 1e-9,
                    "跳發拋球以可靠事件傳遞階段，球以 Server 狀態同步");
        }
    }

    private static UdpCodec.WorldSnapshotFrame snapshot(int sequence, int revision,
                                                         Packet.WorldSnapshot state) throws Exception {
        byte[] bytes = UdpCodec.worldSnapshot(7, sequence, sequence, state);
        check(bytes.length < 1400, "即時快照應避免超過常見網路 MTU 而被分片");
        UdpCodec.WorldSnapshotFrame decoded = (UdpCodec.WorldSnapshotFrame) UdpCodec.decode(bytes, bytes.length);
        check(decoded.snapshotSequence == sequence && decoded.snapshot.collisionRevision == revision,
                "即時快照保留序號與事件版本");
        return decoded;
    }

    private static void testRallyContactsWithoutReliableEvent() throws Exception {
        for (boolean redSide : new boolean[]{true, false}) {
            GameModel server = new GameModel();
            GameModel client = new GameModel();
            server.getServeHandler().setRedServing(!redSide);
            model.player.Team team = redSide ? server.redTeam : server.blueTeam;
            server.recordRegularHit(redSide, team.backPlayer);
            server.recordRegularHit(redSide, team.wingSpiker);
            Packet.WorldSnapshot secondTouch = snapshot(10, 0, Packet.WorldSnapshot.from(server, 0)).snapshot;
            secondTouch.rallyContacts.applyTo(client);
            check(client.getHitCount(redSide) == 2
                            && client.getLastHitter(redSide) == (redSide
                            ? client.redTeam.wingSpiker : client.blueTeam.wingSpiker)
                            && client.isServeReceptionComplete(),
                    "一般第二球即使沒有可靠事件，Client 仍校正觸球次數、上一觸球者與接發狀態");

            server.recordRegularHit(redSide, team.quickAttacker);
            snapshot(11, 0, Packet.WorldSnapshot.from(server, 0))
                    .snapshot.rallyContacts.applyTo(client);
            check(client.getHitCount(redSide) == 3, "一般第三球也由即時快照校正");

            server.resetCounters();
            snapshot(12, 0, Packet.WorldSnapshot.from(server, 0))
                    .snapshot.rallyContacts.applyTo(client);
            check(client.getHitCount(redSide) == 0 && client.getLastHitter(redSide) == null,
                    "過網清零即使沒有可靠事件，Client 仍同步歸零");
        }
    }

    private static void testReliableEventRestoresContactHistory() throws Exception {
        for (boolean redSide : new boolean[]{true, false}) {
            GameModel firstSetter = new GameModel();
            model.player.Team firstTeam = redSide ? firstSetter.redTeam : firstSetter.blueTeam;
            firstSetter.recordHit(redSide, firstTeam.setter);
            firstSetter.recordHit(redSide, firstTeam.backPlayer);
            GameModel firstClient = new GameModel();
            event(1, Packet.EventType.SETTER_CONTACT, Packet.CompactState.from(firstSetter))
                    .state.applyTo(firstClient);
            check(firstClient.hasSetterTouched(redSide) && firstClient.canSetterTouch(redSide),
                    "Setter 接第一球後，可靠事件保留第三球可再碰的歷史");

            GameModel secondSetter = new GameModel();
            model.player.Team secondTeam = redSide ? secondSetter.redTeam : secondSetter.blueTeam;
            secondSetter.recordHit(redSide, secondTeam.backPlayer);
            secondSetter.recordHit(redSide, secondTeam.setter);
            GameModel secondClient = new GameModel();
            event(2, Packet.EventType.SETTER_CONTACT, Packet.CompactState.from(secondSetter))
                    .state.applyTo(secondClient);
            check(secondClient.hasSetterTouched(redSide) && !secondClient.canSetterTouch(redSide),
                    "Setter 接第二球後，可靠事件不誤開第三球觸球資格");

            secondSetter.recordBlock(redSide, secondTeam.quickAttacker);
            snapshot(13, 0, Packet.WorldSnapshot.from(secondSetter, 0))
                    .snapshot.rallyContacts.applyTo(secondClient);
            check(secondClient.hasBlocked(redSide) && secondClient.getHitCount(redSide) == 2,
                    "攔網使用記錄也隨即時快照校正，且攔網不加觸球次數");
            secondSetter.resetCounters();
            event(3, Packet.EventType.FLOW, Packet.CompactState.from(secondSetter))
                    .state.applyTo(secondClient);
            check(!secondClient.hasBlocked(redSide) && !secondClient.hasSetterTouched(redSide),
                    "可靠事件可完整還原重置後的攔網與 Setter 歷史");
        }
    }

    private static void testReliableEventsStayInOrder() throws Exception {
        Packet.CompactState state = Packet.CompactState.from(new GameModel());
        ReliableEventInbox inbox = new ReliableEventInbox();
        UdpCodec.Event first = event(1, Packet.EventType.AIR_SET_CONTACT, state);
        UdpCodec.Event second = event(2, Packet.EventType.RULE, state);
        UdpCodec.Event third = event(3, Packet.EventType.SCORE, state);
        inbox.offer(second);
        inbox.offer(third);
        check(inbox.pollNext() == null, "第二件先到時不跳過第一件");
        inbox.offer(first);
        check(inbox.pollNext() == first && inbox.pollNext() == second
                        && inbox.pollNext() == third,
                "空中舉球、違規、得分事件亂序到達仍按 ID 逐件處理");
        inbox.offer(first);
        check(inbox.pollNext() == null, "重送的事件不重複處理");

        byte[] abortBytes = UdpCodec.matchAborted(7, 4, GameServer.SYNC_TIMEOUT_MESSAGE);
        UdpCodec.MatchAborted aborted = (UdpCodec.MatchAborted) UdpCodec.decode(abortBytes, abortBytes.length);
        inbox.offer(aborted);
        check(inbox.pollNext() == aborted
                        && GameServer.SYNC_TIMEOUT_MESSAGE.equals(aborted.message),
                "中止通知延續事件順序並攜帶原因");
    }

    private static UdpCodec.Event event(int id, Packet.EventType type,
                                         Packet.CompactState state) throws Exception {
        byte[] bytes = UdpCodec.event(7, id, id, type, id, state);
        return (UdpCodec.Event) UdpCodec.decode(bytes, bytes.length);
    }

    private static void testServerKeepsUnacknowledgedEvents() {
        ServerPlayerSlot slot = new ServerPlayerSlot(true, 1, InetAddress.getLoopbackAddress(), 5001);
        Packet.CompactState state = Packet.CompactState.from(new GameModel());
        for (int id = 1; id <= 10; id++) {
            slot.addPendingEvent(new ReliableEvent(id, id, Packet.EventType.SCORE, id, state));
        }
        check(slot.pendingEventsSnapshot().length == 10, "超過原本 8 件上限仍不得默默丟棄");
        slot.acknowledgeEvent(10);
        check(slot.pendingEventsSnapshot().length == 9
                        && slot.pendingEventsSnapshot()[0].event.id == 1,
                "ACK 第 10 件不代表前 9 件已收到");
        check(slot.hasExpiredEvent(System.nanoTime() + 3_000_000_000L, 3_000_000_000L),
                "首件事件逾時可明確中止連線");
        for (int id = 1; id < 10; id++) {
            slot.acknowledgeEvent(id);
        }
        check(!slot.hasPendingEvents(), "逐件 ACK 後清空待確認佇列");
    }

    private static void testControlsStayInOrder() throws Exception {
        ServerPlayerSlot slot = new ServerPlayerSlot(true, 1, InetAddress.getLoopbackAddress(), 5001);
        slot.acceptControl(control(2, UdpCodec.ControlAction.CANCEL_RESET));
        check(slot.pollControl() == null, "控制指令第二件先到不得跳過第一件");
        slot.acceptControl(control(1, UdpCodec.ControlAction.RESET_REQUEST));
        check(slot.pollControl().action == UdpCodec.ControlAction.RESET_REQUEST
                        && slot.pollControl().action == UdpCodec.ControlAction.CANCEL_RESET,
                "重設與取消依發送順序執行");
        check(!slot.acceptControl(control(1, UdpCodec.ControlAction.RESET_REQUEST)),
                "控制指令重送不重複執行");

        byte[] bytes = UdpCodec.controlStatus(7, 9, 2, false, true);
        UdpCodec.ControlStatus status = (UdpCodec.ControlStatus) UdpCodec.decode(bytes, bytes.length);
        check(status.statusSequence == 9 && status.acknowledgedSequence == 2,
                "控制狀態保留自身序號及已確認指令序號");
    }

    private static UdpCodec.ControlFrame control(int sequence, UdpCodec.ControlAction action)
            throws Exception {
        byte[] bytes = UdpCodec.control(7, sequence, action);
        return (UdpCodec.ControlFrame) UdpCodec.decode(bytes, bytes.length);
    }

    private static void testMotionSnapshotKeepsAnimationAlive() {
        GameModel model = new GameModel();
        model.redTeam.setter.playSettingAnimation();
        Packet.PlayerState state = Packet.PlayerState.from(model.redTeam.setter);
        state.applyMotionTo(model.redTeam.setter);
        model.redTeam.setter.update(new TeamInput());
        check(model.redTeam.setter.getAction() == PlayerAction.SETTING,
                "逐 tick 位置校正不重置 Setter 動畫序列");
    }

    private static void testAuthoritativeAttackSwingSnapshot() {
        GameModel server = new GameModel();
        GameModel client = new GameModel();
        server.redTeam.wingSpiker.jumping = true;
        server.redTeam.wingSpiker.startAttackSwingAnimation();
        Packet.PlayerState.from(server.redTeam.wingSpiker)
                .applyMotionTo(client.redTeam.wingSpiker);
        check(client.redTeam.wingSpiker.getAction() == PlayerAction.ATTACK_SWING
                        && client.redTeam.wingSpiker.assetName.contains("attack"),
                "Client 不預播揮臂，但收到 Server 攻擊快照後會顯示");
    }

    private static void testMbSnapshotReconcilesActionAndAsset() {
        GameModel local = new GameModel();
        TeamInput block = new TeamInput();
        block.quickAttack = true;
        local.redTeam.quickAttacker.update(block);
        check(local.redTeam.quickAttacker.getAction() == PlayerAction.BLOCK,
                "本機 MB 預測為攔網");

        GameModel authoritative = new GameModel();
        TeamInput attack = new TeamInput();
        attack.quickAttack = true;
        attack.hasFirstRegularTouch = true;
        authoritative.redTeam.quickAttacker.update(attack);
        Packet.PlayerState state = Packet.PlayerState.from(authoritative.redTeam.quickAttacker);
        state.applyMotionTo(local.redTeam.quickAttacker);
        check(local.redTeam.quickAttacker.getAction() == PlayerAction.ATTACK_READY
                        && local.redTeam.quickAttacker.attackHitBox.enabled
                        && local.redTeam.quickAttacker.assetName.equals(state.assetName),
                "Server 改判 MB 攻擊時，本機圖片與攻擊框一起校正");
        local.redTeam.quickAttacker.update(new TeamInput());
        check(!local.redTeam.quickAttacker.assetName.contains("block"),
                "舊攔網動畫不會在下一幀覆蓋攻擊圖片");
    }

    private static void testAirSetInputAndSnapshotAnimation() {
        TeamInput input = new TeamInput();
        input.airSetModifier = true;
        check(Packet.decodeInput(Packet.encodeInput(input)).airSetModifier,
                "空中舉球方向修正鍵會傳到 Server");
        input.backLeft = true;
        input.backJump = true;
        TeamInput blueWorldInput = input.mirroredHorizontally();
        TeamInput decodedBlueInput = Packet.decodeInput(Packet.encodeInput(blueWorldInput));
        check(decodedBlueInput.airSetModifier && decodedBlueInput.backJump
                        && decodedBlueInput.backRight && !decodedBlueInput.backLeft,
                "藍方網路視角只翻轉水平移動，保留空中舉球與攻擊鍵");

        GameModel local = new GameModel();
        GameModel server = new GameModel();
        local.redTeam.backPlayer.assetName = "player 1 back.png";
        server.redTeam.backPlayer.applyNetworkAction(PlayerAction.DIVE, "player 1 dive2.png");
        Packet.PlayerState.from(server.redTeam.backPlayer).applyMotionTo(local.redTeam.backPlayer);
        check(local.redTeam.backPlayer.getAction() == PlayerAction.DIVE
                        && local.redTeam.backPlayer.assetName.contains("dive"),
                "本機後排快照進入撲球時同步圖片");
        server.redTeam.backPlayer.applyNetworkAction(PlayerAction.DIVE, "player 1 dive3.png");
        Packet.PlayerState.from(server.redTeam.backPlayer).applyMotionTo(local.redTeam.backPlayer);
        check(local.redTeam.backPlayer.assetName.equals("player 1 dive3.png"),
                "本機撲球動畫被校正後仍跟隨 Server 後續圖片");

        local.redTeam.wingSpiker.applyNetworkAction(PlayerAction.RUN_LOOP, "player 1 run1.png");
        Packet.PlayerState.from(server.redTeam.wingSpiker).applyMotionTo(local.redTeam.wingSpiker);
        check(local.redTeam.wingSpiker.getAction() == PlayerAction.IDLE
                        && local.redTeam.wingSpiker.assetName.equals(server.redTeam.wingSpiker.assetName),
                "本機 WS 站定時不保留舊跑步圖片");
    }

    private static void testRemoteInterpolationStopsAfterBoundedExtrapolation() {
        GameModel model = new GameModel();
        model.blueTeam.wingSpiker.x = 700;
        RemotePlayerInterpolator interpolator = new RemotePlayerInterpolator();
        interpolator.observeAt(model.blueTeam.wingSpiker,
                Packet.PlayerState.from(model.blueTeam.wingSpiker), 10, 0);
        model.blueTeam.wingSpiker.x = 710;
        interpolator.observeAt(model.blueTeam.wingSpiker,
                Packet.PlayerState.from(model.blueTeam.wingSpiker), 11, 16_666_666L);
        double interpolated = interpolator.renderedXAt(model.blueTeam.wingSpiker, 41_666_665L);
        double held = interpolator.renderedXAt(model.blueTeam.wingSpiker, 2_000_000_000L);
        check(interpolated > 700 && interpolated < 710, "對手在相鄰快照間平滑插值");
        check(Math.abs(held - 770) < 0.01, "漏包只外推六 tick，之後不繼續猜測位置");
    }

    private static void testSnapshotDrivenVisualEffects() {
        GameModel client = new GameModel();
        client.ball.y = 400;
        client.syncNetworkVisualEffects(true, true);
        client.updateForNetworkPrediction(new TeamInput(), true);
        check(!client.spikeEffect.getTrailPoints().isEmpty(), "Server 軌跡旗標驅動 Client 純視覺軌跡");
        client.ball.y = model.GameConfig.FLOOR_Y - client.ball.radius;
        client.syncNetworkVisualEffects(false, true);
        check(!client.spikeEffect.isSpikeTrailActive()
                        && !client.spikeEffect.getSmokeParticles().isEmpty(),
                "Server 軌跡結束且球落地時仍能顯示煙霧");
    }

    private static void testLocalLoopback() throws Exception {
        GameServer server = new GameServer();
        Thread serverThread = new Thread(server::run, "network-sync-test-server");
        serverThread.start();
        GameModel redModel = new GameModel();
        GameModel blueModel = new GameModel();
        try (GameClient red = new GameClient(redModel, "127.0.0.1");
             GameClient blue = new GameClient(blueModel, "127.0.0.1")) {
            TeamInput idle = new TeamInput();
            long deadline = System.nanoTime() + 3_000_000_000L;
            while ((!red.isConnected() || !blue.isConnected()) && System.nanoTime() < deadline) {
                red.update(idle, false, false);
                blue.update(idle, false, false);
                Thread.sleep(17);
            }
            check(red.isConnected() && blue.isConnected(), "本機兩端可連線並接收對方輸入");
            check(!red.isBluePerspective() && blue.isBluePerspective(), "本機測試紅藍兩端分配正確");

            GameModel authoritativeMb = new GameModel();
            TeamInput mbAttack = new TeamInput();
            mbAttack.quickAttack = true;
            mbAttack.hasFirstRegularTouch = true;
            authoritativeMb.blueTeam.quickAttacker.update(mbAttack);
            Packet.PlayerState.from(authoritativeMb.blueTeam.quickAttacker)
                    .applyMotionTo(redModel.blueTeam.quickAttacker);
            check(redModel.blueTeam.quickAttacker.attackHitBox.enabled
                            && red.getRenderedPlayerAsset(redModel.blueTeam.quickAttacker)
                            .equals(authoritativeMb.blueTeam.quickAttacker.assetName),
                    "對手 MB 的圖片直接對應 Server 攻擊框，不顯示舊攔網圖片");

            double serverBallX = blueModel.ball.x;
            redModel.ball.x += 120;
            for (int tick = 0; tick < 5; tick++) {
                red.update(idle, false, false);
                blue.update(idle, false, false);
                Thread.sleep(17);
            }
            check(Math.abs(redModel.ball.x - serverBallX) < 0.01,
                    "沒有可靠事件時，球仍逐 tick 接受 Server 快照");

            redModel.recordHit(true, redModel.redTeam.backPlayer);
            redModel.recordBlock(true, redModel.redTeam.quickAttacker);
            check(redModel.getHitCount(true) == 1 && redModel.hasBlocked(true),
                    "先製造 Client 本地觸球歷史偏差");
            for (int tick = 0; tick < 5; tick++) {
                red.update(idle, false, false);
                blue.update(idle, false, false);
                Thread.sleep(17);
            }
            check(redModel.getHitCount(true) == 0 && !redModel.hasBlocked(true),
                    "沒有可靠事件時，Client 的觸球次數與攔網歷史仍由 Server 快照校正");

            double serverBallY = blueModel.ball.y;
            double serverBallRotation = blueModel.ball.rotationDegrees;
            redModel.ball.x += 10;
            redModel.ball.y += 7;
            redModel.ball.rotationDegrees += 25;
            boolean smallCorrectionReceived = false;
            for (int tick = 0; tick < 10; tick++) {
                red.update(idle, false, false);
                blue.update(idle, false, false);
                if (Math.abs(redModel.ball.x - serverBallX) < 0.01
                        && Math.abs(redModel.ball.y - serverBallY) < 0.01
                        && Math.abs(redModel.ball.rotationDegrees - serverBallRotation) < 0.01) {
                    check(red.getRenderedBallX(redModel.ball.x) == redModel.ball.x
                                    && red.getRenderedBallY(redModel.ball.y) == redModel.ball.y
                                    && red.getRenderedBallRotation(redModel.ball.rotationDegrees)
                                    == redModel.ball.rotationDegrees,
                            "球心與旋轉角度在收到 Server 快照後立即顯示，不保留平滑偏移");
                    smallCorrectionReceived = true;
                    break;
                }
                Thread.sleep(17);
            }
            check(smallCorrectionReceived, "小幅球心與旋轉誤差仍能收到 Server 快照校正");

            double start = blueModel.blueTeam.backPlayer.x;
            TeamInput move = new TeamInput();
            move.backRight = true;
            for (int tick = 0; tick < 30; tick++) {
                red.update(idle, false, false);
                blue.update(move, false, false);
                Thread.sleep(17);
            }
            check(Math.abs(blueModel.blueTeam.backPlayer.x - start) > 1,
                    "本地按鍵仍立即移動球員");
            check(Math.abs(redModel.blueTeam.backPlayer.x - blueModel.blueTeam.backPlayer.x) < 30,
                    "對方球員由 Server 每 tick 快照校正");

            double wingStart = redModel.blueTeam.wingSpiker.x;
            TeamInput wingAction = new TeamInput();
            wingAction.wingAttack = true;
            Set<String> observedAssets = new HashSet<>();
            for (int tick = 0; tick < 12; tick++) {
                red.update(idle, false, false);
                blue.update(tick == 0 ? wingAction : idle, false, false);
                observedAssets.add(red.getRenderedPlayerAsset(redModel.blueTeam.wingSpiker));
                Thread.sleep(17);
            }
            check(Math.abs(redModel.blueTeam.wingSpiker.x - wingStart) > 20,
                    "沒有可靠事件時，對手 WS 撲球位置仍由快照更新");
            check(observedAssets.contains("player 2 dive1.png")
                            && observedAssets.contains("player 2 dive2.png"),
                    "第一球前對手 WS 仍顯示 Server 的撲球動畫");

            TeamInput heldServe = new TeamInput();
            heldServe.servePressed = true;
            heldServe.backJump = true;
            heldServe.backDive = true;
            long serveDeadline = System.nanoTime() + 1_000_000_000L;
            while (redModel.getServeHandler().getState() != ServeState.IN_PLAY
                    && System.nanoTime() < serveDeadline) {
                red.update(heldServe, false, false);
                blue.update(idle, false, false);
                Thread.sleep(17);
            }
            check(redModel.getServeHandler().getState() == ServeState.IN_PLAY,
                    "連線 Client 收到直接進入 IN_PLAY 的發球事件");
            for (int tick = 0; tick < 5; tick++) {
                red.update(heldServe, false, false);
                blue.update(idle, false, false);
                Thread.sleep(17);
            }
            check(redModel.redTeam.backPlayer.getAction() != PlayerAction.DIVE,
                    "連線發球鍵持續按住時後排不誤撲");
            red.update(idle, false, false);
            blue.update(idle, false, false);
            Thread.sleep(17);
            red.update(heldServe, false, false);
            blue.update(idle, false, false);
            check(redModel.redTeam.backPlayer.getAction() == PlayerAction.DIVE,
                    "連線發球鍵放開後重新按下才撲球");
        } finally {
            server.close();
            serverThread.join(4_000);
            check(!serverThread.isAlive(), "Server 可正常結束");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
