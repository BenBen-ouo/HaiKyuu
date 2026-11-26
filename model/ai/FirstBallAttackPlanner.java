package model.ai;

import java.util.random.RandomGenerator;
import model.ControlInput;
import model.player.PlayerAction;

/** MB 貼網第一球的反應、起跳及再次按鍵；可達窗口由原角色提供。 */
final class FirstBallAttackPlanner {
    private final RandomGenerator random;
    private int readyAt = -1;
    private int launchedAt = -1;

    FirstBallAttackPlanner(RandomGenerator random) { this.random = random; }
    void reset() { readyAt = launchedAt = -1; }
    void react() { readyAt = -1; }

    boolean update(AiObservation o, ControlInput input, int tick, AiDifficulty difficulty, AiMatchLearning learning) {
        var mb = o.own().get(2);
        if (!o.receptionComplete() || o.ownTouches() != 0 || o.attackLocked() || o.lastTouchOwn()) {
            reset(); return false;
        }
        if (launchedAt >= 0 && mb.action() == PlayerAction.ATTACK_READY && mb.jumping()) {
            // 起跳鍵必須放開，下一次按住才可排隊等待進框，與真人操作一致。
            if (tick > launchedAt + 1) {
                if (DefenseForecast.overlaps(o.ball().x(), o.ball().y(), mb.x() + mb.attackOffsetX(),
                        mb.y() + mb.attackOffsetY(), mb.attackWidth(), mb.attackHeight())) {
                    int shot = AttackOptions.choose(o, AiDifficulty.ability(difficulty.attack()), random, 2, false,
                            learning.statistics(), learning.context(o, 0, 2));
                    if (shot >= 0) { input.mbActionPressed = true; OffensePlanner.applyShot(input, shot); }
                } else input.mbActionPressed = true;
            }
            return true;
        }
        if (launchedAt >= 0) { launchedAt = -1; return false; }
        int contact = o.mbFirstBallContactFrames();
        if (contact < 0 || !mb.canStart()) { readyAt = -1; return false; }
        if (!AttackOptions.hasSafeShot(o.ball().xAt(contact), o.ball().yAt(contact),
                AiDifficulty.ability(difficulty.attack()))) return false;
        if (readyAt < 0) readyAt = tick + AiDifficulty.reactionFrames(difficulty.attack(), random);
        // 判讀可達來球期間保留 MB，不讓舊攔網方案搶先把它送入 BLOCK。
        if (tick < readyAt) return true;
        input.mbActionPressed = true;
        launchedAt = tick;
        learning.statistics().plan(AiMatchStatistics.Tactic.ATTACK);
        return true;
    }
}
