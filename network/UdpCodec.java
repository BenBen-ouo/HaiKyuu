/*
純 UDP 二進位協定。
握手、輸入、遠端輸入、可靠事件與重設控制都走 UDP 5001；重要事件以 eventId / ACK 重送。
*/
package network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class UdpCodec {
    private static final int MAGIC = 0x484B5555; // HKUU
    private static final short VERSION = 2;

    private static final byte TYPE_HELLO = 1;
    private static final byte TYPE_WELCOME = 2;
    private static final byte TYPE_REJECT = 3;
    private static final byte TYPE_INPUT = 4;
    private static final byte TYPE_REMOTE_INPUT = 5;
    private static final byte TYPE_EVENT = 6;
    private static final byte TYPE_CONTROL = 7;
    private static final byte TYPE_CONTROL_STATUS = 8;
    private static final byte TYPE_SESSION_END = 9;

    public enum ControlAction {
        RESET_REQUEST,
        CANCEL_RESET,
        DISCONNECT
    }

    private UdpCodec() {
    }

    public interface Decoded {
    }

    public static final class Hello implements Decoded {
        public final long clientNonce;
        public final int protocolVersion;

        private Hello(long clientNonce, int protocolVersion) {
            this.clientNonce = clientNonce;
            this.protocolVersion = protocolVersion;
        }
    }

    public static final class Welcome implements Decoded {
        public final long clientNonce;
        public final long sessionToken;
        public final boolean redSide;
        public final int serverTick;
        public final Packet.CompactState state;

        private Welcome(long clientNonce, long sessionToken, boolean redSide, int serverTick, Packet.CompactState state) {
            this.clientNonce = clientNonce;
            this.sessionToken = sessionToken;
            this.redSide = redSide;
            this.serverTick = serverTick;
            this.state = state;
        }
    }

    public static final class Reject implements Decoded {
        public final long clientNonce;
        public final String message;

        private Reject(long clientNonce, String message) {
            this.clientNonce = clientNonce;
            this.message = message;
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

    public static final class ControlFrame implements Decoded {
        public final long token;
        public final int sequence;
        public final ControlAction action;

        private ControlFrame(long token, int sequence, ControlAction action) {
            this.token = token;
            this.sequence = sequence;
            this.action = action;
        }
    }

    public static final class ControlStatus implements Decoded {
        public final long token;
        public final int acknowledgedSequence;
        public final boolean redResetConfirmed;
        public final boolean blueResetConfirmed;

        private ControlStatus(long token, int acknowledgedSequence, boolean redResetConfirmed, boolean blueResetConfirmed) {
            this.token = token;
            this.acknowledgedSequence = acknowledgedSequence;
            this.redResetConfirmed = redResetConfirmed;
            this.blueResetConfirmed = blueResetConfirmed;
        }
    }

    public static final class SessionEnd implements Decoded {
        public final long token;
        public final String message;

        private SessionEnd(long token, String message) {
            this.token = token;
            this.message = message;
        }
    }

    public static byte[] hello(long clientNonce) throws IOException {
        return write(TYPE_HELLO, out -> {
            out.writeLong(clientNonce);
            out.writeInt(Packet.PROTOCOL_VERSION);
        });
    }

    public static byte[] welcome(
            long clientNonce,
            long sessionToken,
            boolean redSide,
            int serverTick,
            Packet.CompactState state
    ) throws IOException {
        return write(TYPE_WELCOME, out -> {
            out.writeLong(clientNonce);
            out.writeLong(sessionToken);
            out.writeBoolean(redSide);
            out.writeInt(serverTick);
            writeState(out, state);
        });
    }

    public static byte[] reject(long clientNonce, String message) throws IOException {
        return write(TYPE_REJECT, out -> {
            out.writeLong(clientNonce);
            writeNullableString(out, message);
        });
    }

    public static byte[] input(
            long token,
            int sequence,
            int clientTick,
            int inputMask,
            int lastReceivedEventId
    ) throws IOException {
        return write(TYPE_INPUT, out -> {
            out.writeLong(token);
            out.writeInt(sequence);
            out.writeInt(clientTick);
            out.writeInt(inputMask);
            out.writeInt(lastReceivedEventId);
        });
    }

    public static byte[] remoteInput(long token, int serverTick, int inputMask) throws IOException {
        return write(TYPE_REMOTE_INPUT, out -> {
            out.writeLong(token);
            out.writeInt(serverTick);
            out.writeInt(inputMask);
        });
    }

    public static byte[] event(
            long token,
            int eventId,
            int serverTick,
            Packet.EventType type,
            Packet.CompactState state
    ) throws IOException {
        return write(TYPE_EVENT, out -> {
            out.writeLong(token);
            out.writeInt(eventId);
            out.writeInt(serverTick);
            out.writeByte(type.ordinal());
            writeState(out, state);
        });
    }

    public static byte[] control(long token, int sequence, ControlAction action) throws IOException {
        return write(TYPE_CONTROL, out -> {
            out.writeLong(token);
            out.writeInt(sequence);
            out.writeByte(action.ordinal());
        });
    }

    public static byte[] controlStatus(
            long token,
            int acknowledgedSequence,
            boolean redResetConfirmed,
            boolean blueResetConfirmed
    ) throws IOException {
        return write(TYPE_CONTROL_STATUS, out -> {
            out.writeLong(token);
            out.writeInt(acknowledgedSequence);
            out.writeBoolean(redResetConfirmed);
            out.writeBoolean(blueResetConfirmed);
        });
    }

    public static byte[] sessionEnd(long token, String message) throws IOException {
        return write(TYPE_SESSION_END, out -> {
            out.writeLong(token);
            writeNullableString(out, message);
        });
    }

    public static Decoded decode(byte[] data, int length) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, length))) {
            if (in.readInt() != MAGIC || in.readShort() != VERSION) {
                return null;
            }

            return switch (in.readByte()) {
                case TYPE_HELLO -> new Hello(in.readLong(), in.readInt());
                case TYPE_WELCOME -> new Welcome(
                        in.readLong(),
                        in.readLong(),
                        in.readBoolean(),
                        in.readInt(),
                        readState(in)
                );
                case TYPE_REJECT -> new Reject(in.readLong(), readNullableString(in));
                case TYPE_INPUT -> new InputFrame(
                        in.readLong(), in.readInt(), in.readInt(), in.readInt(), in.readInt()
                );
                case TYPE_REMOTE_INPUT -> new RemoteInputFrame(in.readLong(), in.readInt(), in.readInt());
                case TYPE_EVENT -> new Event(
                        in.readLong(),
                        in.readInt(),
                        in.readInt(),
                        readEventType(in.readByte()),
                        readState(in)
                );
                case TYPE_CONTROL -> new ControlFrame(in.readLong(), in.readInt(), readControlAction(in.readByte()));
                case TYPE_CONTROL_STATUS -> new ControlStatus(
                        in.readLong(), in.readInt(), in.readBoolean(), in.readBoolean()
                );
                case TYPE_SESSION_END -> new SessionEnd(in.readLong(), readNullableString(in));
                default -> null;
            };
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static Packet.EventType readEventType(int ordinal) {
        Packet.EventType[] values = Packet.EventType.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Packet.EventType.RULE;
    }

    private static ControlAction readControlAction(int ordinal) {
        ControlAction[] values = ControlAction.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ControlAction.CANCEL_RESET;
    }

    private static byte[] write(byte type, Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeShort(VERSION);
            out.writeByte(type);
            writer.write(out);
            out.flush();
            return bytes.toByteArray();
        }
    }

    private static void writeState(DataOutputStream out, Packet.CompactState state) throws IOException {
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

        out.writeBoolean(state.spikeTrailActive);
        out.writeBoolean(state.spikeTrailRed);
    }

    private static Packet.CompactState readState(DataInputStream in) throws IOException {
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

        boolean spikeTrailActive = in.readBoolean();
        boolean spikeTrailRed = in.readBoolean();

        return new Packet.CompactState(
                ball, redTeam, blueTeam,
                redScore, blueScore,
                redHitCount, blueHitCount,
                redLastHitterIndex, blueLastHitterIndex,
                lastHitTeamCode, lastTouchWasBlock,
                serveStateOrdinal, redServing, rallyOver, deadBallTimer,
                matchOver, matchWinnerCode,
                transientMessage, transientMessageTimer, transientMessageColorCode,
                pendingTouchOut, pendingTouchOutWinnerCode, matchOverCountdownFrames,
                spikeTrailActive, spikeTrailRed
        );
    }

    private static void writeBall(DataOutputStream out, Packet.BallState ball) throws IOException {
        out.writeDouble(ball.x);
        out.writeDouble(ball.y);
        out.writeDouble(ball.vx);
        out.writeDouble(ball.vy);
        out.writeDouble(ball.radius);
        out.writeDouble(ball.rotationDegrees);
        out.writeDouble(ball.rotationSpeed);
        out.writeBoolean(ball.fastFloorBounceSpin);
    }

    private static Packet.BallState readBall(DataInputStream in) throws IOException {
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
        if (count > 8) {
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
    }

    private static Packet.PlayerState readPlayer(DataInputStream in) throws IOException {
        return new Packet.PlayerState(
                readNullableString(in),
                in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(),
                in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean(),
                in.readDouble(), in.readByte(), in.readBoolean()
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

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }
}
