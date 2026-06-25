/*
純 UDP 二進位協定。
UDP 5001 分為 INPUT、BALL_SNAPSHOT、COLLISION_EVENT 三層；事件 ACK、重設控制與對局中止仍走同一 port。
*/
package network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class UdpCodec {
    private static final int MAGIC = 0x484B5555; // HKUU
    private static final short VERSION = 5;

    private static final byte TYPE_HELLO = 1;
    private static final byte TYPE_WELCOME = 2;
    private static final byte TYPE_INPUT = 3;
    private static final byte TYPE_REMOTE_INPUT = 4;
    private static final byte TYPE_EVENT = 5;
    private static final byte TYPE_CONTROL = 6;
    private static final byte TYPE_CONTROL_STATUS = 7;
    private static final byte TYPE_EVENT_ACK = 8;
    private static final byte TYPE_MATCH_ABORTED = 9;
    private static final byte TYPE_BALL_SNAPSHOT = 10;

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

        private Hello(long clientNonce) {
            this.clientNonce = clientNonce;
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

    public static final class InputFrame implements Decoded {
        public final long token;
        public final int sequence;
        public final int scheduledServerTick;
        public final int inputMask;

        private InputFrame(long token, int sequence, int scheduledServerTick, int inputMask) {
            this.token = token;
            this.sequence = sequence;
            this.scheduledServerTick = scheduledServerTick;
            this.inputMask = inputMask;
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
        public final int collisionRevision;
        public final Packet.CompactState state;

        private Event(
                long token,
                int eventId,
                int serverTick,
                Packet.EventType type,
                int collisionRevision,
                Packet.CompactState state
        ) {
            this.token = token;
            this.eventId = eventId;
            this.serverTick = serverTick;
            this.type = type;
            this.collisionRevision = collisionRevision;
            this.state = state;
        }
    }

    /** 不可靠 BALL_SNAPSHOT，只包含球資料與碰撞版本。 */
    public static final class BallSnapshotFrame implements Decoded {
        public final long token;
        public final int serverTick;
        public final int snapshotSequence;
        public final int collisionRevision;
        public final Packet.BallState ball;

        private BallSnapshotFrame(
                long token,
                int serverTick,
                int snapshotSequence,
                int collisionRevision,
                Packet.BallState ball
        ) {
            this.token = token;
            this.serverTick = serverTick;
            this.snapshotSequence = snapshotSequence;
            this.collisionRevision = collisionRevision;
            this.ball = ball;
        }
    }

    public static final class EventAck implements Decoded {
        public final long token;
        public final int eventId;

        private EventAck(long token, int eventId) {
            this.token = token;
            this.eventId = eventId;
        }
    }

    public static final class MatchAborted implements Decoded {
        public final long token;
        public final int eventId;

        private MatchAborted(long token, int eventId) {
            this.token = token;
            this.eventId = eventId;
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

    public static byte[] hello(long clientNonce) throws IOException {
        return write(TYPE_HELLO, out -> out.writeLong(clientNonce));
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
            NetworkStateCodec.writeState(out, state);
        });
    }

    public static byte[] input(long token, int sequence, int scheduledServerTick, int inputMask) throws IOException {
        return write(TYPE_INPUT, out -> {
            out.writeLong(token);
            out.writeInt(sequence);
            out.writeInt(scheduledServerTick);
            out.writeInt(inputMask);
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
            int collisionRevision,
            Packet.CompactState state
    ) throws IOException {
        return write(TYPE_EVENT, out -> {
            out.writeLong(token);
            out.writeInt(eventId);
            out.writeInt(serverTick);
            out.writeByte(type.ordinal());
            out.writeInt(collisionRevision);
            NetworkStateCodec.writeState(out, state);
        });
    }

    public static byte[] ballSnapshot(
            long token,
            int serverTick,
            int snapshotSequence,
            Packet.BallSnapshot snapshot
    ) throws IOException {
        return write(TYPE_BALL_SNAPSHOT, out -> {
            out.writeLong(token);
            out.writeInt(serverTick);
            out.writeInt(snapshotSequence);
            out.writeInt(snapshot.collisionRevision);
            NetworkStateCodec.writeBall(out, snapshot.ball);
        });
    }

    public static byte[] eventAck(long token, int eventId) throws IOException {
        return write(TYPE_EVENT_ACK, out -> {
            out.writeLong(token);
            out.writeInt(eventId);
        });
    }

    public static byte[] matchAborted(long token, int eventId) throws IOException {
        return write(TYPE_MATCH_ABORTED, out -> {
            out.writeLong(token);
            out.writeInt(eventId);
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

    public static Decoded decode(byte[] data, int length) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, length))) {
            if (in.readInt() != MAGIC || in.readShort() != VERSION) {
                return null;
            }

            return switch (in.readByte()) {
                case TYPE_HELLO -> new Hello(in.readLong());
                case TYPE_WELCOME -> new Welcome(
                        in.readLong(),
                        in.readLong(),
                        in.readBoolean(),
                        in.readInt(),
                        NetworkStateCodec.readState(in)
                );
                case TYPE_INPUT -> new InputFrame(in.readLong(), in.readInt(), in.readInt(), in.readInt());
                case TYPE_REMOTE_INPUT -> new RemoteInputFrame(in.readLong(), in.readInt(), in.readInt());
                case TYPE_EVENT -> new Event(
                        in.readLong(),
                        in.readInt(),
                        in.readInt(),
                        readEventType(in.readByte()),
                        in.readInt(),
                        NetworkStateCodec.readState(in)
                );
                case TYPE_EVENT_ACK -> new EventAck(in.readLong(), in.readInt());
                case TYPE_MATCH_ABORTED -> new MatchAborted(in.readLong(), in.readInt());
                case TYPE_CONTROL -> new ControlFrame(in.readLong(), in.readInt(), readControlAction(in.readByte()));
                case TYPE_CONTROL_STATUS -> new ControlStatus(
                        in.readLong(), in.readInt(), in.readBoolean(), in.readBoolean()
                );
                case TYPE_BALL_SNAPSHOT -> new BallSnapshotFrame(
                        in.readLong(),
                        in.readInt(),
                        in.readInt(),
                        in.readInt(),
                        NetworkStateCodec.readBall(in)
                );
                default -> null;
            };
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static Packet.EventType readEventType(int ordinal) {
        Packet.EventType[] values = Packet.EventType.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Packet.EventType.SCORE;
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

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }
}
