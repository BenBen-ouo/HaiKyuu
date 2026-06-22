/*
 * UDP 二進位封包編解碼。
 * 不使用 Java 物件序列化，避免每秒輸入與 correction 產生大量配置、GC 與 TCP 佇列阻塞。
 */
package network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import model.PlayerAction;

public final class UdpCodec {
    private static final int MAGIC = 0x484B5555; // "HKUU"
    private static final short VERSION = 1;

    private static final byte TYPE_HELLO = 1;
    private static final byte TYPE_INPUT = 2;
    private static final byte TYPE_REMOTE_INPUT = 3;
    private static final byte TYPE_CORRECTION = 4;
    private static final byte TYPE_EVENT = 5;

    private UdpCodec() {
    }

    public interface Decoded {
    }

    public static final class Hello implements Decoded {
        public final long token;

        private Hello(long token) {
            this.token = token;
        }
    }

    public static final class InputFrame implements Decoded {
        public final long token;
        public final int sequence;
        public final int clientTick;
        public final int inputMask;
        public final int lastReceivedEventId;

        private InputFrame(long token, int sequence, int clientTick, int inputMask, int lastReceivedEventId) {
            this.token = token;
            this.sequence = sequence;
            this.clientTick = clientTick;
            this.inputMask = inputMask;
            this.lastReceivedEventId = lastReceivedEventId;
        }
    }

    public static final class RemoteInputFrame implements Decoded {
        public final long token;
        public final int serverTick;
        public final int inputMask;

        private RemoteInputFrame(long token, int serverTick, int inputMask) {
            this.token = token;
            this.serverTick = serverTick;
            this.inputMask = inputMask;
        }
    }

    public static final class Correction implements Decoded {
        public final long token;
        public final int serverTick;
        public final int latestEventId;
        public final boolean redResetConfirmed;
        public final boolean blueResetConfirmed;
        public final Packet.CompactState state;

        private Correction(
                long token,
                int serverTick,
                int latestEventId,
                boolean redResetConfirmed,
                boolean blueResetConfirmed,
                Packet.CompactState state
        ) {
            this.token = token;
            this.serverTick = serverTick;
            this.latestEventId = latestEventId;
            this.redResetConfirmed = redResetConfirmed;
            this.blueResetConfirmed = blueResetConfirmed;
            this.state = state;
        }
    }

    public static final class Event implements Decoded {
        public final long token;
        public final int eventId;
        public final int serverTick;
        public final Packet.EventType type;
        public final Packet.CompactState state;

        private Event(long token, int eventId, int serverTick, Packet.EventType type, Packet.CompactState state) {
            this.token = token;
            this.eventId = eventId;
            this.serverTick = serverTick;
            this.type = type;
            this.state = state;
        }
    }

    public static byte[] hello(long token) throws IOException {
        return write(TYPE_HELLO, output -> output.writeLong(token));
    }

    public static byte[] input(
            long token,
            int sequence,
            int clientTick,
            int inputMask,
            int lastReceivedEventId
    ) throws IOException {
        return write(TYPE_INPUT, output -> {
            output.writeLong(token);
            output.writeInt(sequence);
            output.writeInt(clientTick);
            output.writeInt(inputMask);
            output.writeInt(lastReceivedEventId);
        });
    }

    public static byte[] remoteInput(long token, int serverTick, int inputMask) throws IOException {
        return write(TYPE_REMOTE_INPUT, output -> {
            output.writeLong(token);
            output.writeInt(serverTick);
            output.writeInt(inputMask);
        });
    }

    public static byte[] correction(
            long token,
            int serverTick,
            int latestEventId,
            boolean redResetConfirmed,
            boolean blueResetConfirmed,
            Packet.CompactState state
    ) throws IOException {
        return write(TYPE_CORRECTION, output -> {
            output.writeLong(token);
            output.writeInt(serverTick);
            output.writeInt(latestEventId);
            output.writeBoolean(redResetConfirmed);
            output.writeBoolean(blueResetConfirmed);
            writeState(output, state);
        });
    }

    public static byte[] event(
            long token,
            int eventId,
            int serverTick,
            Packet.EventType type,
            Packet.CompactState state
    ) throws IOException {
        return write(TYPE_EVENT, output -> {
            output.writeLong(token);
            output.writeInt(eventId);
            output.writeInt(serverTick);
            output.writeByte(type.ordinal());
            writeState(output, state);
        });
    }

    public static Decoded decode(byte[] data, int length) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data, 0, length))) {
            if (input.readInt() != MAGIC || input.readShort() != VERSION) {
                return null;
            }

            byte type = input.readByte();
            return switch (type) {
                case TYPE_HELLO -> new Hello(input.readLong());
                case TYPE_INPUT -> new InputFrame(
                        input.readLong(),
                        input.readInt(),
                        input.readInt(),
                        input.readInt(),
                        input.readInt()
                );
                case TYPE_REMOTE_INPUT -> new RemoteInputFrame(
                        input.readLong(),
                        input.readInt(),
                        input.readInt()
                );
                case TYPE_CORRECTION -> new Correction(
                        input.readLong(),
                        input.readInt(),
                        input.readInt(),
                        input.readBoolean(),
                        input.readBoolean(),
                        readState(input)
                );
                case TYPE_EVENT -> new Event(
                        input.readLong(),
                        input.readInt(),
                        input.readInt(),
                        readEventType(input.readByte()),
                        readState(input)
                );
                default -> null;
            };
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static Packet.EventType readEventType(int ordinal) {
        Packet.EventType[] values = Packet.EventType.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Packet.EventType.BALL_IMPACT;
    }

    private static byte[] write(byte type, Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeByte(type);
            writer.write(output);
            output.flush();
            return bytes.toByteArray();
        }
    }

    private static void writeState(DataOutputStream output, Packet.CompactState state) throws IOException {
        writeBall(output, state.ball);
        writeTeam(output, state.redTeam);
        writeTeam(output, state.blueTeam);

        output.writeInt(state.redScore);
        output.writeInt(state.blueScore);
        output.writeInt(state.redHitCount);
        output.writeInt(state.blueHitCount);
        output.writeInt(state.redLastHitterIndex);
        output.writeInt(state.blueLastHitterIndex);
        output.writeByte(state.lastHitTeamCode);
        output.writeBoolean(state.lastTouchWasBlock);

        output.writeByte(state.serveStateOrdinal);
        output.writeBoolean(state.redServing);
        output.writeBoolean(state.rallyOver);
        output.writeInt(state.deadBallTimer);

        output.writeBoolean(state.matchOver);
        output.writeByte(state.matchWinnerCode);
        writeNullableString(output, state.transientMessage);
        output.writeInt(state.transientMessageTimer);
        output.writeByte(state.transientMessageColorCode);
        output.writeBoolean(state.pendingTouchOut);
        output.writeByte(state.pendingTouchOutWinnerCode);
        output.writeInt(state.matchOverCountdownFrames);

        output.writeBoolean(state.spikeTrailActive);
        output.writeBoolean(state.spikeTrailRed);
    }

    private static Packet.CompactState readState(DataInputStream input) throws IOException {
        Packet.BallState ball = readBall(input);
        Packet.TeamState redTeam = readTeam(input);
        Packet.TeamState blueTeam = readTeam(input);

        int redScore = input.readInt();
        int blueScore = input.readInt();
        int redHitCount = input.readInt();
        int blueHitCount = input.readInt();
        int redLastHitterIndex = input.readInt();
        int blueLastHitterIndex = input.readInt();
        int lastHitTeamCode = input.readByte();
        boolean lastTouchWasBlock = input.readBoolean();

        int serveStateOrdinal = input.readByte();
        boolean redServing = input.readBoolean();
        boolean rallyOver = input.readBoolean();
        int deadBallTimer = input.readInt();

        boolean matchOver = input.readBoolean();
        int matchWinnerCode = input.readByte();
        String transientMessage = readNullableString(input);
        int transientMessageTimer = input.readInt();
        int transientMessageColorCode = input.readByte();
        boolean pendingTouchOut = input.readBoolean();
        int pendingTouchOutWinnerCode = input.readByte();
        int matchOverCountdownFrames = input.readInt();

        boolean spikeTrailActive = input.readBoolean();
        boolean spikeTrailRed = input.readBoolean();

        return new Packet.CompactState(
                ball,
                redTeam,
                blueTeam,
                redScore,
                blueScore,
                redHitCount,
                blueHitCount,
                redLastHitterIndex,
                blueLastHitterIndex,
                lastHitTeamCode,
                lastTouchWasBlock,
                serveStateOrdinal,
                redServing,
                rallyOver,
                deadBallTimer,
                matchOver,
                matchWinnerCode,
                transientMessage,
                transientMessageTimer,
                transientMessageColorCode,
                pendingTouchOut,
                pendingTouchOutWinnerCode,
                matchOverCountdownFrames,
                spikeTrailActive,
                spikeTrailRed
        );
    }

    private static void writeBall(DataOutputStream output, Packet.BallState ball) throws IOException {
        output.writeDouble(ball.x);
        output.writeDouble(ball.y);
        output.writeDouble(ball.vx);
        output.writeDouble(ball.vy);
        output.writeDouble(ball.radius);
        output.writeDouble(ball.rotationDegrees);
        output.writeDouble(ball.rotationSpeed);
        output.writeBoolean(ball.fastFloorBounceSpin);
    }

    private static Packet.BallState readBall(DataInputStream input) throws IOException {
        return new Packet.BallState(
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readBoolean()
        );
    }

    private static void writeTeam(DataOutputStream output, Packet.TeamState team) throws IOException {
        output.writeByte(team.players.length);
        for (Packet.PlayerState player : team.players) {
            writePlayer(output, player);
        }
    }

    private static Packet.TeamState readTeam(DataInputStream input) throws IOException {
        int count = Byte.toUnsignedInt(input.readByte());
        if (count > 8) {
            throw new IOException("Invalid player count");
        }

        Packet.PlayerState[] players = new Packet.PlayerState[count];
        for (int i = 0; i < count; i++) {
            players[i] = readPlayer(input);
        }
        return new Packet.TeamState(players);
    }

    private static void writePlayer(DataOutputStream output, Packet.PlayerState player) throws IOException {
        writeNullableString(output, player.assetName);
        output.writeDouble(player.x);
        output.writeDouble(player.y);
        output.writeDouble(player.vx);
        output.writeDouble(player.vy);
        output.writeBoolean(player.jumping);
        output.writeBoolean(player.attacking);
        output.writeBoolean(player.blocking);
        output.writeBoolean(player.diving);
        output.writeBoolean(player.mirrorImage);
        output.writeDouble(player.jumpStartX);
        output.writeByte(player.actionOrdinal);
        output.writeBoolean(player.attackHitBoxEnabled);
    }

    private static Packet.PlayerState readPlayer(DataInputStream input) throws IOException {
        return new Packet.PlayerState(
                readNullableString(input),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readDouble(),
                input.readByte(),
                input.readBoolean()
        );
    }

    private static void writeNullableString(DataOutputStream output, String value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeUTF(value);
        }
    }

    private static String readNullableString(DataInputStream input) throws IOException {
        return input.readBoolean() ? input.readUTF() : null;
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }
}
