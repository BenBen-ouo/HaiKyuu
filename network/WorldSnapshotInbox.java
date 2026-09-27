package network;

/** 只接受最新快照；跨過尚未處理事件的快照先暫存。 */
final class WorldSnapshotInbox {
    private int lastAppliedSequence = -1;
    private UdpCodec.WorldSnapshotFrame pending;

    UdpCodec.WorldSnapshotFrame offer(UdpCodec.WorldSnapshotFrame frame, int appliedRevision) {
        if (frame.snapshotSequence <= lastAppliedSequence
                || (pending != null && frame.snapshotSequence <= pending.snapshotSequence)
                || frame.snapshot.collisionRevision < appliedRevision) {
            return null;
        }
        if (frame.snapshot.collisionRevision > appliedRevision) {
            pending = frame;
            return null;
        }
        lastAppliedSequence = frame.snapshotSequence;
        return frame;
    }

    UdpCodec.WorldSnapshotFrame releaseAfterEvent(int appliedRevision) {
        if (pending == null || pending.snapshot.collisionRevision > appliedRevision) {
            return null;
        }
        UdpCodec.WorldSnapshotFrame frame = pending;
        pending = null;
        if (frame.snapshot.collisionRevision != appliedRevision
                || frame.snapshotSequence <= lastAppliedSequence) {
            return null;
        }
        lastAppliedSequence = frame.snapshotSequence;
        return frame;
    }
}
