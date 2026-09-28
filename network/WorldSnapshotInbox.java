package network;

/** 位置快照獨立於可靠裁決事件，只接受新的序號與不過期的版本。 */
final class WorldSnapshotInbox {
    private int lastAppliedSequence = -1;

    UdpCodec.WorldSnapshotFrame offer(UdpCodec.WorldSnapshotFrame frame, int appliedRevision) {
        if (frame.snapshotSequence <= lastAppliedSequence
                || frame.snapshot.collisionRevision < appliedRevision) {
            return null;
        }
        lastAppliedSequence = frame.snapshotSequence;
        return frame;
    }
}
