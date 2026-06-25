/*
序列化與還原 UDP 封包內的球、球員、隊伍與完整遊戲快照。
欄位順序固定，必須與目前協定版本完全一致，避免 Client 與 Server 解讀不同步。
*/
package network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

final class NetworkStateCodec {
    private static final int MAX_PLAYERS_PER_TEAM = 8;

    private NetworkStateCodec() {
    }

    static void writeState(DataOutputStream out, Packet.CompactState state) throws IOException {
        writeBall(out, state.ball);
        writeTeam(out, state.redTeam);
        writeTeam(out, state.blueTeam);

        out.writeInt(state.redScore);
        out.writeInt(state.blueScore);
        out.writeInt(state.redHitCount);
        out.writeInt(state.blueHitCount);
        out.writeInt(state.redLastHitterIndex);
        out.writeInt(state.blueLastHitterIndex);
        out.writeByte(state.lastHitTeamCode);
        out.writeBoolean(state.lastTouchWasBlock);

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
        out.writeInt(state.matchOverCountdownFrames);
    }

    static Packet.CompactState readState(DataInputStream in) throws IOException {
        Packet.BallState ball = readBall(in);
        Packet.TeamState redTeam = readTeam(in);
        Packet.TeamState blueTeam = readTeam(in);

        int redScore = in.readInt();
        int blueScore = in.readInt();
        int redHitCount = in.readInt();
        int blueHitCount = in.readInt();
        int redLastHitterIndex = in.readInt();
        int blueLastHitterIndex = in.readInt();
        int lastHitTeamCode = in.readByte();
        boolean lastTouchWasBlock = in.readBoolean();

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
        int matchOverCountdownFrames = in.readInt();

        return new Packet.CompactState(
                ball, redTeam, blueTeam,
                redScore, blueScore,
                redHitCount, blueHitCount,
                redLastHitterIndex, blueLastHitterIndex,
                lastHitTeamCode, lastTouchWasBlock,
                serveStateOrdinal, redServing, rallyOver, deadBallTimer,
                matchOver, matchWinnerCode,
                transientMessage, transientMessageTimer, transientMessageColorCode,
                pendingTouchOut, pendingTouchOutWinnerCode, matchOverCountdownFrames
        );
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
        return new Packet.BallState(
                in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(),
                in.readDouble(), in.readDouble(), in.readDouble(), in.readBoolean()
        );
    }

    private static void writeTeam(DataOutputStream out, Packet.TeamState team) throws IOException {
        out.writeByte(team.players.length);
        for (Packet.PlayerState player : team.players) {
            writePlayer(out, player);
        }
    }

    private static Packet.TeamState readTeam(DataInputStream in) throws IOException {
        int count = Byte.toUnsignedInt(in.readByte());
        if (count > MAX_PLAYERS_PER_TEAM) {
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
        out.writeBoolean(player.attacking);
        out.writeBoolean(player.blocking);
        out.writeBoolean(player.diving);
        out.writeBoolean(player.mirrorImage);
        out.writeDouble(player.jumpStartX);
        out.writeByte(player.actionOrdinal);
        out.writeBoolean(player.attackHitBoxEnabled);
        out.writeDouble(player.hitBoxOffsetX);
        out.writeDouble(player.hitBoxOffsetY);
        out.writeDouble(player.hitBoxWidth);
        out.writeDouble(player.hitBoxHeight);
        out.writeInt(player.hitBoxArcWidth);
        out.writeInt(player.hitBoxArcHeight);
        out.writeDouble(player.hitBoxRotationDegrees);
    }

    private static Packet.PlayerState readPlayer(DataInputStream in) throws IOException {
        return new Packet.PlayerState(
                readNullableString(in),
                in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(),
                in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean(),
                in.readDouble(), in.readByte(), in.readBoolean(),
                in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(),
                in.readInt(), in.readInt(), in.readDouble()
        );
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
