package model.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;
import model.ControlInput;
import model.GameConfig;
import model.ball.BallTrajectory;
import model.player.BackPlayer;
import model.player.PlayerAction;
import model.player.WingSpiker;

/** 保留戰術意圖，依實際球路、站位與可達窗口更新操作；不修改任何角色或球。 */
final class OffensePlanner {
    private static final int BACK = 0, SETTER = 1, MB = 2, WS = 3;
    private static final int[] ATTACKERS = {WS, BACK, MB};
    private static final double TRAVEL_COST_WEIGHT = .08;
    private static final double TIMING_COST_WEIGHT = .2;
    private static final double AIR_SET_COST = 12;
    private static final double WS_PREFERENCE = 8;
    private record SetForecast(BallForecast flight, int delay, int setterJumpDelay, boolean confirmed) {}
    private record Window(int role, int startTick, int contactTick, double startX, double score, boolean airSet) {}

    private final RandomGenerator random;
    private final OpponentHabits habits;
    private final boolean[] launched = new boolean[4];
    private final Window[] decoys = new Window[4];
    private Window selected;
    private boolean planned;
    private boolean negative;
    private boolean quickSet;
    private boolean jumpSet;
    private boolean preferAirSet;
    private boolean airSetAllowed;
    private double skill;
    private int beginTick;
    private int nextPlan;
    private int negativeLead;
    private int timingOffset;
    private int participants;
    private int selectedLaunchTick = -1;
    private boolean waitingForSetter;
    private boolean backReserved;
    private boolean protectBackReturn;

    boolean isBackReserved() {
        if (backReserved) return true;
        if (selected == null || selected.airSet()) return false;
        int used = 1;
        for (int role : ATTACKERS) {
            if (role != selected.role() && launched[role]) used++;
        }
        for (int role : ATTACKERS) {
            if (decoys[role] != null && !launched[role] && used < participants) {
                used++;
                if (role == BACK) return true;
            }
        }
        return false;
    }

    OffensePlanner(RandomGenerator random, OpponentHabits habits) {
        this.random = random;
        this.habits = habits;
    }

    void clear() {
        planned = false;
        selected = null;
        selectedLaunchTick = -1;
        backReserved = false;
        Arrays.fill(launched, false);
        Arrays.fill(decoys, null);
    }

    void begin(AiObservation o, int tick, AiDifficulty difficulty) {
        clear();
        planned = true;
        beginTick = tick;
        skill = AiDifficulty.ability(difficulty.attack());
        protectBackReturn = habits.shouldKeepBackForReturn(difficulty.defense());
        negative = random.nextDouble() < AiTuning.MAX_NEGATIVE_TEMPO_PROBABILITY
                * AiTuning.unlockedProgress(difficulty.attack(), AiTuning.MIN_NEGATIVE_TEMPO_ATTACK);
        // 接觸在下一 tick 才被觀察到，預留一幀，避免實際提前量超出 48 幀。
        negativeLead = random.nextInt(AiTuning.NEGATIVE_START_MIN_LEAD_FRAMES,
                Math.max(AiTuning.NEGATIVE_START_MIN_LEAD_FRAMES + 1, AiTuning.NEGATIVE_START_MAX_LEAD_FRAMES));
        timingOffset = random.nextInt(-AiTuning.ATTACK_TIMING_JITTER_FRAMES, AiTuning.ATTACK_TIMING_JITTER_FRAMES + 1);
        participants = 1;
        if (random.nextDouble() < AiTuning.unlockedProgress(difficulty.attack(), AiTuning.MIN_TWO_ATTACKERS)) {
            participants = 2;
            if (random.nextDouble() < AiTuning.unlockedProgress(difficulty.attack(), AiTuning.MIN_THREE_ATTACKERS)) {
                participants = 3;
            }
        }
        jumpSet = random.nextDouble() < AiTuning.MAX_JUMP_SET_PROBABILITY
                * AiTuning.unlockedProgress(difficulty.attack(), AiTuning.MIN_JUMP_SET_ATTACK);
        quickSet = difficulty.attack() >= AiTuning.MIN_QUICK_SET_ATTACK && o.ownLastRole() != SETTER
                && random.nextDouble() < AiTuning.QUICK_SET_BASE_PROBABILITY + skill * AiTuning.QUICK_SET_SKILL_PROBABILITY;
        airSetAllowed = difficulty.attack() >= AiTuning.MIN_AIR_SET_ATTACK;
        preferAirSet = airSetAllowed && o.ownTouches() == 1 && o.ownLastRole() == SETTER
                && random.nextDouble() < AiTuning.MAX_AIR_SET_PROBABILITY
                    * AiTuning.unlockedProgress(difficulty.attack(), AiTuning.MIN_AIR_SET_ATTACK);
        waitingForSetter = o.ownTouches() == 1 && o.ownLastRole() != SETTER;
        nextPlan = tick;
    }

    void onSet(AiObservation o, int tick, AiDifficulty difficulty) {
        if (!planned) begin(o, tick, difficulty);
        waitingForSetter = false;
        preferAirSet = false;
        nextPlan = tick;
        // 已起跳者只依實際飛行碰球，不能在空中重新安排起跳。
        if (selected == null || !launched[selected.role()] || o.ownLastRole() == selected.role()) selected = null;
    }

    void update(AiObservation o, ControlInput input, int tick, AiDifficulty difficulty) {
        if (!planned) begin(o, tick, difficulty);
        backReserved = launched[BACK];
        if (selected != null && !launched[selected.role()] && tick > selected.contactTick()) selected = null;
        SetForecast set = forecast(o);
        if (waitingForSetter && quickSet) input.spikeShort = true;
        if (waitingForSetter && jumpSet && !o.own().get(SETTER).jumping()
                && set.setterJumpDelay() == 0) input.setterJump = true;
        if (selected != null && selected.airSet() && selected.role() == WS && o.ownTouches() == 1) {
            backReserved = true;
            var back = o.own().get(BACK);
            double thirdTouchX = GameConfig.NET_X - GameConfig.THREE_METER_PX - back.width() / 2
                    - AiTuning.BACK_TAKEOFF_LINE_MARGIN_PX;
            moveBackTo(input, back, thirdTouchX);
        }

        if ((selected == null || !launched[selected.role()]) && tick >= nextPlan) {
            selected = plan(o, set, tick);
            nextPlan = tick + AiTuning.REPLAN_INTERVAL_FRAMES;
            if (selected != null) {
                for (int role : ATTACKERS) {
                    decoys[role] = role == selected.role() || launched[role] ? null : findWindow(o, set, role, tick, false);
                    if (role == BACK && decoys[role] != null && unsafeBackReturn(o, decoys[role], selected.contactTick())) {
                        decoys[role] = null;
                    }
                }
            }
        }

        if (selected != null && (selected.role() == BACK || selected.airSet() && selected.role() == WS)) {
            backReserved = true;
        }
        if (selected == null && o.ownTouches() == 1 && o.ownLastRole() == SETTER) {
            backReserved = true;
            // 無可達攻擊／已解鎖跳舉窗口時，仍以原有一般碰撞追接第二球。
            var back = o.own().get(BACK);
            if (back.canStart()) {
                int arrival = o.ball().descendingArrival(back.receiveY());
                DefensePlanner.move(input, back, o.ball().xAt(arrival));
            }
        }

        if (selected != null && !launched[selected.role()]) {
            if (selected.role() == BACK) backReserved = true;
            if (selected.role() == BACK) moveBackTo(input, o.own().get(BACK), selected.startX());
            if (tick >= selected.startTick() && canLaunch(o, selected)) {
                press(input, selected.role());
                launched[selected.role()] = true;
                selectedLaunchTick = tick;
            }
        }

        if (selected != null && launched[selected.role()] && tick > selectedLaunchTick + 1) {
            var p = o.own().get(selected.role());
            if (p.jumping() && p.action() == PlayerAction.ATTACK_READY
                    && (!waitingForSetter || o.ownTouches() >= 2)) {
                boolean setting = selected.airSet() && o.ownTouches() == 1;
                if (overlaps(p, o.ball().x(), o.ball().y())) {
                    if (!setting && betterContactSoon(o, p)) return;
                    int shot = setting ? 0 : AttackOptions.choose(o, skill, random, selected.role() == MB);
                    if (shot >= 0 && o.ownLastRole() != selected.role()) {
                        press(input, selected.role());
                        if (setting) input.airSetModifier = true;
                        else applyShot(input, shot);
                        // 不讓後排假跑的方向鍵覆蓋本幀已選球路，或意外合成空中舉球組合。
                        return;
                    }
                } else {
                    // 提前重新按住只排隊等球；球進框時再用當下接觸點選安全球路。
                    press(input, selected.role());
                }
            }
        }

        if (selected != null && !selected.airSet() && !o.attackLocked()) {
            int used = 1;
            for (int role : ATTACKERS) {
                if (role != selected.role() && launched[role]) used++;
            }
            for (int role : ATTACKERS) {
                Window fake = decoys[role];
                if (fake != null && !launched[role] && used < participants) {
                    used++;
                    // 後排假跑也必須真的走到合法起跳點；不可用假設的位置直接起跳。
                    if (role == BACK && selected.role() != BACK) moveBackTo(input, o.own().get(BACK), fake.startX());
                    if (role == BACK) backReserved = true;
                    if (Math.abs(tick - fake.startTick()) <= AiTuning.ATTACK_TIMING_JITTER_FRAMES && canLaunch(o, fake)) {
                        press(input, role);
                        launched[role] = true;
                    }
                }
            }
        }

        if (selected != null && launched[selected.role()] && !o.own().get(selected.role()).jumping()
                && o.own().get(selected.role()).action() != PlayerAction.RUN_APPROACH
                && tick > selectedLaunchTick + 2) {
            selected = null;
            nextPlan = tick;
        }
    }

    private Window plan(AiObservation o, SetForecast set, int tick) {
        List<Window> candidates = new ArrayList<>(3);
        for (int role : ATTACKERS) {
            if (launched[role]) continue;
            Window window = findWindow(o, set, role, tick, preferAirSet);
            if (window != null) candidates.add(window);
        }
        if (airSetAllowed && candidates.isEmpty() && o.ownTouches() == 1 && o.ownLastRole() == SETTER) {
            // Setter 第一球後不能再接第二球：明確改由 WS／後排空中舉球救回組織。
            for (int role : new int[]{WS, BACK}) {
                if (!launched[role]) {
                    Window window = findWindow(o, set, role, tick, true);
                    if (window != null) candidates.add(window);
                }
            }
        }
        if (candidates.isEmpty() && negative && !set.confirmed()) {
            // 已錯過負節奏就等真正舉出的第二節奏，不以另一個隨機提早量冒充。
            negative = false;
            return plan(o, set, tick);
        }
        int earliestOtherContact = candidates.stream().filter(w -> w.role() != BACK)
                .mapToInt(Window::contactTick).min().orElse(Integer.MAX_VALUE);
        if (earliestOtherContact != Integer.MAX_VALUE) {
            candidates.removeIf(w -> w.role() == BACK && unsafeBackReturn(o, w, earliestOtherContact));
        }
        return candidates.stream().min(java.util.Comparator.comparingDouble(Window::score)).orElse(null);
    }

    private boolean unsafeBackReturn(AiObservation o, Window backWindow, int attackAt) {
        if (!protectBackReturn || backWindow.airSet()) return false;
        var back = o.own().get(BACK);
        int flight = (int) Math.ceil(-2 * GameConfig.BACK_JUMP_VELOCITY_Y / GameConfig.GRAVITY);
        double vx = BackPlayer.plannedJumpVelocityXAt(backWindow.startX(), back.width(),
                back.attackOffsetX(), back.attackWidth(), true);
        double landedCenter = Math.min(GameConfig.NET_X + GameConfig.PLAYER_NET_OVERLAP_X - back.width() / 2,
                backWindow.startX() + back.width() / 2 + vx * flight);
        double middle = (GameConfig.COURT_LEFT_X + GameConfig.NET_X - GameConfig.THREE_METER_PX) / 2;
        double receiveOffset = back.receiveOffsetX() + back.receiveWidth() / 2 - back.width() / 2;
        double readyCenter = habits.expectedFastDeepLanding(middle + receiveOffset) - receiveOffset;
        int returnFrames = (int) Math.ceil(Math.abs(landedCenter - readyCenter) / GameConfig.BACK_RUN_START_SPEED);
        return backWindow.startTick() + flight + returnFrames > attackAt + AiTuning.FAST_OPPONENT_RETURN_FRAMES;
    }

    private Window findWindow(AiObservation o, SetForecast set, int role, int tick, boolean setting) {
        if (setting && !airSetAllowed) return null;
        var p = o.own().get(role);
        if (launched[role] || p.jumping() || p.diving()
                || p.action() == PlayerAction.RUN_RETURN || p.action() == PlayerAction.RUN_APPROACH) return null;
        if (setting && (role == MB || o.ownLastRole() == role)) return null;
        if (role == MB && !quickSet) return null;
        if (role == MB && set.confirmed()) {
            // 開網球即使還能作攔網動畫，也不能安排無法碰到的 MB 攻擊。
            int peak = apexFrames(set.flight().velocityY());
            double x = set.flight().xAt(peak);
            if (x < p.x() + p.attackOffsetX() - GameConfig.BALL_RADIUS
                    || x > p.x() + p.attackOffsetX() + p.attackWidth() + GameConfig.BALL_RADIUS) return null;
        }
        int approach = role == WS ? WingSpiker.approachDurationFrames() : 0;
        double vy = role == BACK ? GameConfig.BACK_JUMP_VELOCITY_Y
                : role == MB ? GameConfig.MB_JUMP_VELOCITY_Y : GameConfig.WS_JUMP_VELOCITY_Y;
        int flight = (int) Math.ceil(-2 * vy / GameConfig.GRAVITY);
        int nominal = role == MB ? set.delay() : negative && !set.confirmed()
                ? set.delay() - negativeLead : set.delay() + apexFrames(set.flight().velocityY());
        if (!setting && negative && !set.confirmed() && nominal < 0) return null;
        boolean negativeStart = negative && !set.confirmed() && role != MB && !setting;
        int min = setting ? 0 : Math.max(0, nominal - (negativeStart ? 0 : AiTuning.ATTACK_TIMING_JITTER_FRAMES));
        int max = setting ? AiTuning.FORECAST_FRAMES - flight : Math.max(0, nominal + (negativeStart ? 0 : AiTuning.ATTACK_TIMING_JITTER_FRAMES));
        if (role == MB) { min = Math.max(0, set.delay() - AiTuning.FIRST_TEMPO_WINDOW_FRAMES); max = set.delay() + AiTuning.FIRST_TEMPO_WINDOW_FRAMES; }
        Window best = null;
        for (int start = min; start <= max; start++) {
            int wait = p.action() == PlayerAction.RECEIVING
                    ? Math.max(0, AiTuning.RECEIVE_RECOVERY_ESTIMATE_FRAMES - (tick - beginTick)) : 0;
            if (start < wait) continue;
            for (int age = 2; age < flight; age++) {
                int t = start + approach + age;
                if (t >= AiTuning.FORECAST_FRAMES || t < set.delay()) continue;
                double bx = set.flight().xAt(t - set.delay());
                double by = set.flight().yAt(t - set.delay());
                if (setting && by < GameConfig.SETTER_SET_APEX_Y) continue;
                double py = GameConfig.FLOOR_Y - p.height() + vy * age + GameConfig.GRAVITY * age * (age + 1) / 2;
                double startX = p.x();
                double px = startX + (role == WS ? WingSpiker.approachDistance() : 0);
                if (role == BACK) {
                    startX = bx - p.attackOffsetX() - p.attackWidth() / 2 - BackPlayer.BACK_JUMP_MAX_HORIZONTAL_SPEED * age;
                    for (int iteration = 0; iteration < 3; iteration++) {
                        double vx = BackPlayer.plannedJumpVelocityXAt(startX, p.width(), p.attackOffsetX(), p.attackWidth(), true);
                        startX = bx - p.attackOffsetX() - p.attackWidth() / 2 - vx * age;
                    }
                    double limit = setting ? GameConfig.NET_X + GameConfig.PLAYER_NET_OVERLAP_X - p.width()
                            : GameConfig.NET_X - GameConfig.THREE_METER_PX - p.width() / 2 - AiTuning.BACK_TAKEOFF_LINE_MARGIN_PX;
                    // 球可在框的邊緣命中；理想中心超過三米線時先退到合法位置，再檢查真實重疊。
                    startX = Math.max(GameConfig.WORLD_LEFT, Math.min(limit, startX));
                    if (Math.abs(startX - p.x()) > runReach(Math.max(0, start - wait))) continue;
                    px = startX + BackPlayer.plannedJumpVelocityXAt(startX, p.width(), p.attackOffsetX(), p.attackWidth(), true) * age;
                }
                if (!overlapsAt(p, px, py, bx, by)) continue;
                if (!setting && !AttackOptions.hasSafeShot(bx, by, skill)) continue;
                double centerY = py + p.attackOffsetY() + p.attackHeight() / 2;
                double margin = Math.abs(centerY - by);
                double travel = role == BACK ? Math.abs(startX - p.x()) : 0;
                double score = margin + travel * TRAVEL_COST_WEIGHT
                        + Math.abs(start - nominal - (negativeStart ? 0 : timingOffset)) * TIMING_COST_WEIGHT
                        + (setting ? AIR_SET_COST : 0) + (role == WS ? -WS_PREFERENCE : 0);
                if (best == null || score < best.score()) best = new Window(role, tick + start, tick + t, startX, score, setting);
            }
        }
        return best;
    }

    private SetForecast forecast(AiObservation o) {
        if (!waitingForSetter || o.ownTouches() >= 2 || o.ownLastRole() == SETTER) return new SetForecast(o.ball(), 0, -1, true);
        var setter = o.own().get(SETTER);
        int contact = -1, jumpDelay = -1;
        if (setter.jumping()) contact = setterContact(o, 0, true);
        else if (jumpSet) {
            for (int delay = 0; delay < AiTuning.FORECAST_FRAMES / 2; delay++) {
                int candidate = setterContact(o, delay, true);
                if (candidate >= 0 && (contact < 0 || candidate < contact)) { contact = candidate; jumpDelay = delay; }
            }
        }
        if (contact < 0) { contact = setterContact(o, 0, false); jumpDelay = -1; }
        if (contact < 0) contact = o.ball().descendingArrival(setter.receiveY());
        double x = o.ball().xAt(contact);
        double y = o.ball().yAt(contact);
        var launch = BallTrajectory.toApex(y, quickSet ? GameConfig.QUICK_SET_APEX_Y : GameConfig.SETTER_SET_APEX_Y, GameConfig.GRAVITY);
        return new SetForecast(new BallForecast(x, y, quickSet
                ? (GameConfig.RED_QUICK_SET_APEX_X - x) / launch.durationFrames() : 0, launch.velocityY()),
                contact, jumpDelay, false);
    }

    private static int setterContact(AiObservation o, int delay, boolean jump) {
        var p = o.own().get(SETTER);
        for (int t = 1; t < AiTuning.FORECAST_FRAMES; t++) {
            int age = Math.max(0, t - delay);
            double vy = p.jumping() ? p.velocityY() : GameConfig.SETTER_JUMP_VELOCITY_Y;
            double y = p.y() + (jump ? vy * age + GameConfig.GRAVITY * age * (age + 1) / 2 : 0);
            if (jump && age > -2 * vy / GameConfig.GRAVITY) continue;
            if (Math.abs(o.ball().xAt(t) - p.receiveX()) <= p.receiveWidth() / 2 + GameConfig.BALL_RADIUS
                    && Math.abs(o.ball().yAt(t) - y - p.receiveOffsetY() - p.receiveHeight() / 2)
                        <= p.receiveHeight() / 2 + GameConfig.BALL_RADIUS) return t;
        }
        return -1;
    }

    private static boolean canLaunch(AiObservation o, Window window) {
        var p = o.own().get(window.role());
        if (!p.canStart()) return false;
        if (window.role() != BACK) return true;
        if (Math.abs(p.x() - window.startX()) > AiTuning.MOVEMENT_DEAD_ZONE_PX) return false;
        return window.airSet() || p.centerX() < GameConfig.NET_X - GameConfig.THREE_METER_PX;
    }

    private static boolean betterContactSoon(AiObservation o, AiObservation.Pose p) {
        if (AttackOptions.hasNormalShot(o.ball().x(), o.ball().y())) return false;
        for (int t = 1; t <= AiTuning.CONTACT_PATIENCE_FRAMES; t++) {
            double x = o.ball().xAt(t), y = o.ball().yAt(t);
            double px = p.x() + p.velocityX() * t;
            double py = p.y() + p.velocityY() * t + GameConfig.GRAVITY * t * (t + 1) / 2;
            if (overlapsAt(p, px, py, x, y) && AttackOptions.hasNormalShot(x, y)) return true;
        }
        return false;
    }

    private static int apexFrames(double vy) { return Math.max(0, (int) Math.ceil(-vy / GameConfig.GRAVITY) - 1); }
    private static double runReach(int frames) {
        return Math.min(frames, GameConfig.BACK_RUN_FIRST_STAGE_END_FRAMES) * GameConfig.BACK_RUN_START_SPEED
                + Math.max(0, Math.min(frames, GameConfig.BACK_RUN_SECOND_STAGE_END_FRAMES) - GameConfig.BACK_RUN_FIRST_STAGE_END_FRAMES) * GameConfig.BACK_RUN_SECOND_STAGE_SPEED
                + Math.max(0, Math.min(frames, GameConfig.BACK_RUN_THIRD_STAGE_END_FRAMES) - GameConfig.BACK_RUN_SECOND_STAGE_END_FRAMES) * GameConfig.BACK_RUN_THIRD_STAGE_SPEED
                + Math.max(0, Math.min(frames, GameConfig.BACK_RUN_FOURTH_STAGE_END_FRAMES) - GameConfig.BACK_RUN_THIRD_STAGE_END_FRAMES) * GameConfig.BACK_RUN_FOURTH_STAGE_SPEED
                + Math.max(0, frames - GameConfig.BACK_RUN_FOURTH_STAGE_END_FRAMES) * GameConfig.BACK_RUN_MAX_SPEED;
    }
    private static void moveBackTo(ControlInput input, AiObservation.Pose p, double x) {
        DefensePlanner.move(input, p, x + p.receiveOffsetX() + p.receiveWidth() / 2);
    }
    private static boolean overlaps(AiObservation.Pose p, double x, double y) { return overlapsAt(p, p.x(), p.y(), x, y); }
    private static boolean overlapsAt(AiObservation.Pose p, double x, double y, double bx, double by) {
        double dx = Math.max(0, Math.abs(bx - x - p.attackOffsetX() - p.attackWidth() / 2) - p.attackWidth() / 2);
        double dy = Math.max(0, Math.abs(by - y - p.attackOffsetY() - p.attackHeight() / 2) - p.attackHeight() / 2);
        return dx * dx + dy * dy <= GameConfig.BALL_RADIUS * GameConfig.BALL_RADIUS;
    }
    static void press(ControlInput input, int role) {
        if (role == BACK) { input.backJump = true; input.backDive = true; }
        else if (role == MB) input.mbActionPressed = true;
        else if (role == WS) input.wingActionPressed = true;
    }
    static void applyShot(ControlInput input, int mode) {
        input.spikeFlat = mode == 1 || mode == 4;
        input.spikeShort = mode == 2;
        input.spikeLob = mode >= 3;
        input.backLeft = false;
        input.backRight = input.spikeFlat;
    }
}
