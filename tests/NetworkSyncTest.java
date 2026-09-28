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
        testReliableEventsStayInOrder();
        testServerKeepsUnacknowledgedEvents();
        testControlsStayInOrder();
        testMotionSnapshotKeepsAnimationAlive();
        testMbSnapshotReconcilesActionAndAsset();
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

    private static UdpCodec.WorldSnapshotFrame snapshot(int sequence, int revision,
                                                         Packet.WorldSnapshot state) throws Exception {
        byte[] bytes = UdpCodec.worldSnapshot(7, sequence, sequence, state);
        check(bytes.length < 1400, "即時快照應避免超過常見網路 MTU 而被分片");
        UdpCodec.WorldSnapshotFrame decoded = (UdpCodec.WorldSnapshotFrame) UdpCodec.decode(bytes, bytes.length);
        check(decoded.snapshotSequence == sequence && decoded.snapshot.collisionRevision == revision,
                "即時快照保留序號與事件版本");
        return decoded;
    }

    private static void testReliableEventsStayInOrder() throws Exception {
        Packet.CompactState state = Packet.CompactState.from(new GameModel());
        ReliableEventInbox inbox = new ReliableEventInbox();
        UdpCodec.Event first = event(1, state);
        UdpCodec.Event second = event(2, state);
        inbox.offer(second);
        check(inbox.pollNext() == null, "第二件先到時不跳過第一件");
        inbox.offer(first);
        check(inbox.pollNext() == first && inbox.pollNext() == second,
                "亂序事件按 ID 逐件處理");
        inbox.offer(first);
        check(inbox.pollNext() == null, "重送的事件不重複處理");

        byte[] abortBytes = UdpCodec.matchAborted(7, 3, GameServer.SYNC_TIMEOUT_MESSAGE);
        UdpCodec.MatchAborted aborted = (UdpCodec.MatchAborted) UdpCodec.decode(abortBytes, abortBytes.length);
        inbox.offer(aborted);
        check(inbox.pollNext() == aborted
                        && GameServer.SYNC_TIMEOUT_MESSAGE.equals(aborted.message),
                "中止通知延續事件順序並攜帶原因");
    }

    private static UdpCodec.Event event(int id, Packet.CompactState state) throws Exception {
        byte[] bytes = UdpCodec.event(7, id, id, Packet.EventType.SCORE, id, state);
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
                    "沒有可靠事件時，對手 WS 助跑位置仍由快照更新");
            check(observedAssets.contains("player 2 run1.png")
                            && observedAssets.contains("player 2 run2.png"),
                    "沒有可靠事件時，對手 WS 仍顯示 Server 的跑步動畫");

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
