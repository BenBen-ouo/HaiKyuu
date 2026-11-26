package model.rally;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import model.ball.Ball;
import model.player.Player;
import model.player.PlayerAction;
import model.player.Team;

/** 每個更新幀的有序已確認觸球；序號跨幀遞增，避免重讀同一幀時重複統計。 */
public final class ContactLog {
    private static final AtomicLong STREAMS = new AtomicLong();
    private final long streamId = STREAMS.incrementAndGet();
    private long sequence;
    private final List<ContactEvent> events = new ArrayList<>(4);
    public long streamId() { return streamId; }
    public void beginFrame() { events.clear(); }
    public List<ContactEvent> snapshot() { return List.copyOf(events); }
    public void record(Team team, Player hitter, int touch, ContactEvent.Kind kind, ContactEvent.Route route, Ball ball) {
        int role = hitter == team.backPlayer ? 0 : hitter == team.setter ? 1 : hitter == team.quickAttacker ? 2
                : hitter == team.wingSpiker ? 3 : -1;
        if (role < 0) throw new IllegalArgumentException("Contact player does not belong to team");
        int mask = 0;
        var players = team.getPlayers();
        for (int i : new int[]{0, 2, 3}) {
            var p = players[i];
            if (p.getAction() == PlayerAction.ATTACK_READY || p.getAction() == PlayerAction.RUN_APPROACH
                    || p.getAction() == PlayerAction.ATTACK_SWING || p.getAction() == PlayerAction.AIR_SETTING) mask |= 1 << i;
        }
        events.add(new ContactEvent(++sequence, team.redSide, role, touch, kind, route,
                ball.x, ball.y, ball.vx, ball.vy, hitter.jumping, mask));
    }
}
