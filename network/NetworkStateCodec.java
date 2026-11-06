/*
序列化與還原 UDP 封包內的球、球員、隊伍與完整遊戲快照。
欄位順序固定，必須與目前協定版本完全一致，避免 Client 與 Server 解讀不同步。
*/
package network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import model.player.PlayerAction;
import model.serve.ServeState;

final class NetworkStateCodec {
    private static final int PLAYERS_PER_TEAM = 4;
    // 協定防護範圍，遠大於目前場地與速度；避免有限但極大的值溢位幾何運算。
    private static final double MAX_ABSOLUTE_PHYSICS_VALUE = 1_000_000;
    private static final int PLAYER_ACTION_COUNT = PlayerAction.values().length;
    private static final int SERVE_STATE_COUNT = ServeState.values().length;

    private NetworkStateCodec() {
    }

    static void writeState(DataOutputStream out, Packet.CompactState state) throws IOException {
        writeBall(out, state.ball);
        writeTeam(out, state.redTeam);
        writeTeam(out, state.blueTeam);

        out.writeInt(state.redScore);
        out.writeInt(state.blueScore);
        writeRallyContacts(out, state.rallyContacts);

        out.writeByte(state.serveStateOrdinal);
        out.writeBoolean(state.redServing);
        out.writeBoolean(state.rallyOver);
        out.writeInt(state.deadBallTimer);

        out.writeBoolean(state.matchOver);
        out.writeByte(state.matchWinnerCode);
        writeNullableString(out, state.transientMessage);
        out.writeInt(state.transientMessageTimer);
        out.writeByte(state.transientMessageColorCode);
        out.writeBoolean(state.pendingTouchOut);
        out.writeByte(state.pendingTouchOutWinnerCode);
        out.writeInt(state.netFlashFrames);
        out.writeBoolean(state.landingSmoke);
    }

    static Packet.CompactState readState(DataInputStream in) throws IOException {
        Packet.BallState ball = readBall(in);
        Packet.TeamState redTeam = readTeam(in);
        Packet.TeamState blueTeam = readTeam(in);

        int redScore = in.readInt();
        int blueScore = in.readInt();
        Packet.RallyContactState rallyContacts = readRallyContacts(in);

        int serveStateOrdinal = in.readByte();
        boolean redServing = in.readBoolean();
        boolean rallyOver = in.readBoolean();
        int deadBallTimer = in.readInt();

        boolean matchOver = in.readBoolean();
        int matchWinnerCode = in.readByte();
        String transientMessage = readNullableString(in);
        int transientMessageTimer = in.readInt();
        int transientMessageColorCode = in.readByte();
        boolean pendingTouchOut = in.readBoolean();
        int pendingTouchOutWinnerCode = in.readByte();

        Packet.CompactState state = new Packet.CompactState(
                ball, redTeam, blueTeam,
                redScore, blueScore,
                rallyContacts,
                serveStateOrdinal, redServing, rallyOver, deadBallTimer,
                matchOver, matchWinnerCode,
                transientMessage, transientMessageTimer, transientMessageColorCode,
                pendingTouchOut, pendingTouchOutWinnerCode, in.readInt(), in.readBoolean()
        );
        require(redScore >= 0 && blueScore >= 0 && deadBallTimer >= 0 && transientMessageTimer >= 0
                && state.netFlashFrames >= 0, "Invalid score or duration");
        require(serveStateOrdinal >= 0 && serveStateOrdinal < SERVE_STATE_COUNT,
                "Invalid serve state");
        require(isNullableBoolean(matchWinnerCode) && isNullableBoolean(transientMessageColorCode)
                && isNullableBoolean(pendingTouchOutWinnerCode), "Invalid team code");
        return state;
    }

    static void writeRallyContacts(DataOutputStream out, Packet.RallyContactState state) throws IOException {
        out.writeInt(state.redHitCount);
        out.writeInt(state.blueHitCount);
        out.writeInt(state.redLastHitterIndex);
        out.writeInt(state.blueLastHitterIndex);
        out.writeByte(state.lastHitTeamCode);
        out.writeBoolean(state.lastTouchWasBlock);
        out.writeBoolean(state.serveReceptionComplete);
        out.writeBoolean(state.redSetterTouched);
        out.writeBoolean(state.blueSetterTouched);
        out.writeBoolean(state.redSetterTouchedFirst);
        out.writeBoolean(state.blueSetterTouchedFirst);
        out.writeBoolean(state.redBlockUsed);
        out.writeBoolean(state.blueBlockUsed);
        out.writeBoolean(state.redAttackLocked);
        out.writeBoolean(state.blueAttackLocked);
    }

    static Packet.RallyContactState readRallyContacts(DataInputStream in) throws IOException {
        Packet.RallyContactState state = new Packet.RallyContactState(
                in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                in.readByte(), in.readBoolean(), in.readBoolean(),
                in.readBoolean(), in.readBoolean(), in.readBoolean(),
                in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean()
        );
        require(state.redHitCount >= 0 && state.redHitCount <= 4
                && state.blueHitCount >= 0 && state.blueHitCount <= 4, "Invalid contact count");
        require(state.redLastHitterIndex >= -1 && state.redLastHitterIndex < PLAYERS_PER_TEAM
                && state.blueLastHitterIndex >= -1 && state.blueLastHitterIndex < PLAYERS_PER_TEAM
                && isNullableBoolean(state.lastHitTeamCode), "Invalid contact owner");
        return state;
    }

    static void writeBall(DataOutputStream out, Packet.BallState ball) throws IOException {
        out.writeDouble(ball.x);
        out.writeDouble(ball.y);
        out.writeDouble(ball.vx);
        out.writeDouble(ball.vy);
        out.writeDouble(ball.radius);
        out.writeDouble(ball.rotationDegrees);
        out.writeDouble(ball.rotationSpeed);
        out.writeBoolean(ball.fastFloorBounceSpin);
    }

    static Packet.BallState readBall(DataInputStream in) throws IOException {
        Packet.BallState ball = new Packet.BallState(
                in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(),
                in.readDouble(), in.readDouble(), in.readDouble(), in.readBoolean()
        );
        requireFinite(ball.x, ball.y, ball.vx, ball.vy, ball.radius, ball.rotationDegrees, ball.rotationSpeed);
        require(ball.radius > 0, "Invalid ball radius");
        return ball;
    }

    static void writeTeam(DataOutputStream out, Packet.TeamState team) throws IOException {
        out.writeByte(team.players.size());
        for (Packet.PlayerState player : team.players) {
            writePlayer(out, player);
        }
    }

    static Packet.TeamState readTeam(DataInputStream in) throws IOException {
        int count = Byte.toUnsignedInt(in.readByte());
        if (count != PLAYERS_PER_TEAM) {
            throw new IOException("Invalid player count");
        }

        Packet.PlayerState[] players = new Packet.PlayerState[count];
        for (int i = 0; i < count; i++) {
            players[i] = readPlayer(in);
        }
        return new Packet.TeamState(players);
    }

    private static void writePlayer(DataOutputStream out, Packet.PlayerState player) throws IOException {
        writeNullableString(out, player.assetName);
        out.writeDouble(player.x);
        out.writeDouble(player.y);
        out.writeDouble(player.vx);
        out.writeDouble(player.vy);
        out.writeBoolean(player.jumping);
        out.writeBoolean(player.diving);
        out.writeBoolean(player.mirrorImage);
        out.writeDouble(player.jumpStartCenterX);
        out.writeByte(player.actionOrdinal);
        out.writeByte(player.animationFrameIndex);
        out.writeBoolean(player.attackHitBoxEnabled);
        out.writeInt(player.countedBlockJumps);
        out.writeBoolean(player.opponentFirstTouchSeen);
        out.writeDouble(player.hitBoxOffsetX);
        out.writeDouble(player.hitBoxOffsetY);
        out.writeDouble(player.hitBoxWidth);
        out.writeDouble(player.hitBoxHeight);
        out.writeInt(player.hitBoxArcWidth);
        out.writeInt(player.hitBoxArcHeight);
        out.writeDouble(player.hitBoxRotationDegrees);
    }

    private static Packet.PlayerState readPlayer(DataInputStream in) throws IOException {
        Packet.PlayerState player = new Packet.PlayerState(
                readNullableString(in),
                in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(),
                in.readBoolean(), in.readBoolean(), in.readBoolean(),
                in.readDouble(), in.readByte(), in.readUnsignedByte(), in.readBoolean(),
                in.readInt(), in.readBoolean(),
                in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(),
                in.readInt(), in.readInt(), in.readDouble()
        );
        requireFinite(player.x, player.y, player.vx, player.vy, player.hitBoxOffsetX,
                player.hitBoxOffsetY, player.hitBoxWidth, player.hitBoxHeight, player.hitBoxRotationDegrees);
        if (!Double.isNaN(player.jumpStartCenterX)) requireFinite(player.jumpStartCenterX);
        require(player.hitBoxWidth > 0 && player.hitBoxHeight > 0
                && player.hitBoxArcWidth >= 0 && player.hitBoxArcHeight >= 0,
                "Invalid hitbox dimensions");
        require(player.actionOrdinal >= 0 && player.actionOrdinal < PLAYER_ACTION_COUNT
                && player.countedBlockJumps >= 0 && player.assetName != null, "Invalid player state");
        require(player.actionOrdinal != PlayerAction.BLOCK.ordinal() || player.animationFrameIndex <= 2,
                "Invalid block animation phase");
        return player;
    }

    private static boolean isNullableBoolean(int code) {
        return code >= 0 && code <= 2;
    }

    private static void requireFinite(double... values) throws IOException {
        for (double value : values) require(Double.isFinite(value)
                && Math.abs(value) <= MAX_ABSOLUTE_PHYSICS_VALUE, "Invalid physics value");
    }

    private static void require(boolean valid, String message) throws IOException {
        if (!valid) throw new IOException(message);
    }

    private static void writeNullableString(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value);
        }
    }

    private static String readNullableString(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }
}
