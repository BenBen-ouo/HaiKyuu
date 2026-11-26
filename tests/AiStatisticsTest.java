package model.ai;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import model.ControlInput;
import model.GameConfig;
import model.GameModel;
import model.TeamInput;
import model.rally.ContactEvent;
import model.rally.RallyContactHandler;
import static model.ai.AiMatchStatistics.*;
import static model.rally.ContactEvent.Kind.*;
import static model.rally.ContactEvent.Route.*;

/** 已確認觸球、歸因、抽樣承諾及學習的行為驗收；不依圖片名稱推測成功。 */
public final class AiStatisticsTest {
    public static void main(String[] args) {
        testAttributionAndMirroring();
        testBlocksAndServes();
        testPlansAndWeights();
        testLearningChangesActualChoices();
        testSecondChoiceAndActualContacts();
        testSecondChoiceAfterOtherReceivers();
        testRecognition();
        testRealMatchJournal();
        System.out.println("AiStatisticsTest passed");
    }
    private static final Context CONTEXT = new Context(Tempo.FIRST, Setting.STANDING, 2, true, false);
    private static final Key KEY = new Key(true, 2, 3, FLAT, false);
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static final class Fixture {
        final boolean red;
        final GameModel game = new GameModel();
        final OpponentHabits habits = new OpponentHabits();
        final AiMatchLearning learning;
        long id;
        int tick, ownScore, otherScore;
        Fixture(boolean red) {
            this.red = red; learning = new AiMatchLearning(red, habits);
            feed(List.of(), 600, false, "");
        }
        ContactEvent event(boolean own, int role, int touch, ContactEvent.Kind kind,
                ContactEvent.Route route, double x) {
            return new ContactEvent(++id, own == red, role, touch, kind, route,
                    red ? x : GameConfig.SCREEN_WIDTH - x, 430, red ? 5 : -5, 2, true, (1 << role) & 13);
        }
        void feed(List<ContactEvent> contacts, double x, boolean landing, String judgment) {
            var base = AiObservation.capture(game, red);
            var observation = new AiObservation(new BallForecast(x, landing ? GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS : 430, 0, 2),
                    base.own(), base.opponent(), base.ownTouches(), base.opponentTouches(), base.ownLastRole(), base.lastTouchOwn(),
                    true, false, false, base.serveState(), base.ownServing(), !judgment.isEmpty(), false,
                    false, landing, false, base.opponentBlock(), contacts, ownScore, otherScore, judgment, base.contactStreamId());
            learning.observe(observation, ++tick);
        }
        void organization(boolean own) {
            feed(List.of(event(own, 0, 1, REGULAR, FREE_BALL, 300),
                    event(own, 1, 2, REGULAR, FREE_BALL, 550),
                    event(own, 2, 3, ATTACK, FLAT, 590)), 590, false, "");
        }
        Summary shot(boolean own, int role, int touch, ContactEvent.Route route, boolean serve) {
            return learning.statistics().shots().get(new Key(own, role, touch, route, serve));
        }
    }
    private static void testAttributionAndMirroring() {
        for (boolean red : new boolean[]{true, false}) {
            var f = new Fixture(red);
            f.organization(true);
            var attack = f.event(true, 2, 3, ATTACK, FLAT, 590);
            // 重複讀取相同事件不可再記一次出手。
            f.feed(List.of(new ContactEvent(3, attack.redSide(), 2, 3, ATTACK, FLAT,
                    attack.x(), attack.y(), attack.vx(), attack.vy(), true, 4)), 590, false, "");
            check(f.shot(true, 2, 3, FLAT, false).attempts() == 1, "事件 ID 去重");
            f.feed(List.of(f.event(false, 0, 1, REGULAR, FREE_BALL, 1050)), 1050, false, "");
            f.ownScore++;
            f.feed(List.of(), 1000, true, "四觸違規");
            var saved = f.shot(true, 2, 3, FLAT, false);
            check(saved.outcomes().equals(Map.of(Outcome.SAVED, 1)) && saved.points().isEmpty(),
                    "對手已接起、之後再失誤，不能算先前攻擊直接得分");
            check(saved.receptions().get(Zone.DEEP) == 1 && saved.landings().isEmpty(), "實際接球位置與落地分開");

            f = new Fixture(red); f.organization(true); f.ownScore++;
            f.feed(List.of(), 1050, true, "IN");
            var direct = f.shot(true, 2, 3, FLAT, false);
            check(direct.outcomes().get(Outcome.DIRECT_POINT) == 1 && direct.points().get(PointReason.LANDING) == 1
                    && direct.landings().get(Zone.DEEP) == 1, "左右一致的直接得分與落點");
            f.feed(List.of(), 1050, true, "IN");
            check(f.shot(true, 2, 3, FLAT, false).outcomes().get(Outcome.DIRECT_POINT) == 1, "死球不重記");
            check(f.learning.statistics().details().keySet().stream().anyMatch(d -> d.context().setting() == Setting.JUMP),
                    "以觸球時 Setter 的空中狀態分類跳舉");
            f.learning.reset();
            check(f.learning.statistics().shots().isEmpty(), "整場重開清空打法統計");
        }
        var habits = new OpponentHabits(); habits.recordAttack(0, -1, 0);
        check(Math.abs(habits.tempoWeight(2) - 1.0 / 3) < .00001,
                "二次攻擊沒有 Setter 出球時間，不能當第二節奏");
        check(zone(590) == Zone.FRONT && zone(400) == Zone.MIDDLE && zone(150) == Zone.DEEP
                && zone(-50) == Zone.OUT && zone(1050) == Zone.DEEP, "鏡像三米分區");
    }
    private static void testBlocksAndServes() {
        var f = new Fixture(true); f.organization(true);
        f.feed(List.of(f.event(false, 2, 0, BLOCK, FREE_BALL, 600)), 600, false, "");
        f.otherScore++; f.feed(List.of(), 300, true, "IN");
        check(f.shot(true, 2, 3, FLAT, false).outcomes().get(Outcome.BLOCKED_LOSS) == 1, "被攔死獨立分類");

        f = new Fixture(true); f.organization(true);
        f.feed(List.of(f.event(false, 2, 0, BLOCK, FREE_BALL, 600),
                f.event(true, 0, 1, REGULAR, FREE_BALL, 300)), 300, false, "");
        check(f.shot(true, 2, 3, FLAT, false).outcomes().get(Outcome.BLOCK_RETURNED) == 1,
                "攔回後自己救起不算被攔死或對手接起");

        f = new Fixture(false); f.organization(true);
        f.feed(List.of(f.event(false, 2, 0, BLOCK, FREE_BALL, 600)), 600, false, "");
        f.ownScore++; f.feed(List.of(), 1180, true, "TOUCH OUT");
        check(f.shot(true, 2, 3, FLAT, false).points().get(PointReason.TOUCH_OUT) == 1, "打 MB 出界獨立原因");

        f = new Fixture(true); f.organization(false);
        f.feed(List.of(f.event(true, 2, 0, BLOCK, FREE_BALL, 600)), 600, false, "");
        f.ownScore++; f.feed(List.of(), 900, true, "IN");
        check(f.learning.statistics().blocks().contacts() == 1 && f.learning.statistics().blocks().points() == 1,
                "攔網碰球與直接攔網得分分開");
        f = new Fixture(true); f.organization(false);
        f.feed(List.of(f.event(true, 2, 0, BLOCK, FREE_BALL, 600)), 600, false, "");
        f.otherScore++; f.feed(List.of(), -40, true, "TOUCH OUT");
        check(f.learning.statistics().blocks().outLosses() == 1, "自己 MB 被打出界不可當攔網成功");

        f = new Fixture(true);
        f.feed(List.of(f.event(true, 0, 0, SERVE, JUMP_SLOW, 100)), 100, false, "");
        f.ownScore++; f.feed(List.of(), 1000, true, "IN");
        check(f.shot(true, 0, 0, JUMP_SLOW, true).points().get(PointReason.SERVE) == 1, "發球得分獨立記錄");
        check(f.learning.statistics().shots().size() == 1, "發球不混入攻擊");
        f = new Fixture(true); f.organization(true); f.otherScore++;
        f.feed(List.of(), 1180, true, "OUT");
        check(f.shot(true, 2, 3, FLAT, false).outcomes().get(Outcome.SELF_ERROR) == 1, "攻擊自身出界");
    }
    private static void testPlansAndWeights() {
        var stats = new AiMatchStatistics();
        stats.plan(Tactic.AIR_SET); stats.plan(Tactic.AIR_SET); stats.finishPlans();
        check(stats.plans().get(Tactic.AIR_SET).equals(new Plan(1, 0, 1)) && stats.shots().isEmpty(),
                "未碰球的安排只記未完成，不是出手");
        stats.plan(Tactic.SECOND_ATTACK); stats.complete(Tactic.SECOND_ATTACK); stats.finishPlans();
        check(stats.plans().get(Tactic.SECOND_ATTACK).equals(new Plan(1, 1, 0)), "安排與完成分開");
        for (int i = 0; i < 7; i++) {
            check(stats.selectionWeight(KEY, CONTEXT, 100) == 1, "七次以前不得學習");
            stats.attempt(KEY, CONTEXT); stats.result(KEY, CONTEXT, Outcome.DIRECT_POINT, PointReason.LANDING, Zone.DEEP, null);
        }
        check(stats.selectionWeight(KEY, CONTEXT, 100) > 1 && stats.selectionWeight(KEY, CONTEXT, 60) == 1,
                "七次後僅困難以上啟用平滑學習");
        for (int i = 0; i < 300; i++) {
            stats.attempt(KEY, CONTEXT); stats.result(KEY, CONTEXT, Outcome.DIRECT_POINT, PointReason.LANDING, Zone.DEEP, null);
        }
        check(stats.selectionWeight(KEY, CONTEXT, 100) < 1.25, "權重上限 25%，不改物理");
        check(stats.selectionWeight(new Key(true, 2, 2, FLAT, false), CONTEXT, 100) == 1,
                "第二／第三球不混樣本");
        check(stats.selectionWeight(new Key(false, 2, 3, FLAT, false), CONTEXT, 100) == 1, "對手打法不能當自己成功率");
        stats.predictionStarted(); stats.checkPrediction(2, 2, Tempo.FIRST, Tempo.SECOND);
        check(stats.blocks().attackerCorrect() == 1 && stats.blocks().tempoCorrect() == 0,
                "猜對攻擊者不代表猜對節奏");
        boolean immutable = false;
        try { stats.shots().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "查詢統計不可修改權威資料");
    }
    private static GameModel firstBall(boolean red) {
        var game = new GameModel();
        game.getServeHandler().setRedServing(!red);
        game.getServeHandler().setWaitingForServe(true);
        game.getServeHandler().setWaitingForServe(false);
        var own = red ? game.redTeam : game.blueTeam;
        game.recordRegularHit(red, own.setter);
        return game;
    }
    private static void testSecondChoiceAndActualContacts() {
        for (boolean red : new boolean[]{true, false}) {
            int actualSets = 0, actualAttacks = 0;
            for (int ability : new int[]{80, 100}) {
                int[] choices = new int[OffensePlanner.SecondChoice.values().length];
                for (int seed = 0; seed < 240; seed++) {
                    var game = firstBall(red);
                    var team = red ? game.redTeam : game.blueTeam;
                    game.ball.x = red ? 550 : 650; game.ball.y = 330; game.ball.vx = 0; game.ball.vy = -3;
                    var habits = new OpponentHabits();
                    var learning = new AiMatchLearning(red, habits);
                    var planner = new OffensePlanner(new Random(seed * 7919L), habits, learning);
                    var difficulty = new AiDifficulty(80, ability);
                    planner.begin(AiObservation.capture(game, red), 0, difficulty);
                    planner.update(AiObservation.capture(game, red), new ControlInput(), 0, difficulty);
                    var choice = planner.secondChoice(); choices[choice.ordinal()]++;
                    check(choice != OffensePlanner.SecondChoice.UNDECIDED, "合法空中第二球窗口應做一次選擇");
                    for (int t = 1; t < 100 && !game.isRallyOverForNetwork(); t++) {
                        var before = AiObservation.capture(game, red);
                        var input = new ControlInput(); planner.update(before, input, t, difficulty);
                        var world = red ? input : input.mirroredHorizontally();
                        game.update(red ? world : new ControlInput(), red ? new ControlInput() : world);
                        for (var e : game.contactEvents()) if (e.redSide() == red && e.touch() == 2) {
                            check(e.role() != 1, "Setter 第一球不能自己接第二球");
                            if (e.kind() == AIR_SET) actualSets++;
                            if (e.kind() == ATTACK) actualAttacks++;
                        }
                        if (game.getHitCount(red) >= 2) break;
                        check(planner.secondChoice() == choice || planner.secondChoice() == OffensePlanner.SecondChoice.SETTER,
                                "窗口消失只能回正常組織，不能重新抽戰術");
                    }
                }
                int air = choices[OffensePlanner.SecondChoice.AIR_SET.ordinal()];
                int attack = choices[OffensePlanner.SecondChoice.ATTACK.ordinal()];
                System.out.printf("secondChoice side=%s skill=%d air=%d attack=%d normal=%d%n", red, ability, air, attack,
                        choices[OffensePlanner.SecondChoice.SETTER.ordinal()]);
                check(air > (ability == 80 ? 15 : 45) && air < (ability == 80 ? 65 : 100), "15%／30% 主動假扣真舉分布");
                check(attack > (ability == 80 ? 10 : 35) && attack < (ability == 80 ? 60 : 90), "未選舉球後分開抽 15%／35%");
            }
            check(actualSets > 0 && actualAttacks > 0, "安排必須能變成實際空中舉球與二次攻擊");
        }
        var game = firstBall(true); game.ball.x = 100; game.ball.y = 200; game.ball.vx = -10;
        var habits = new OpponentHabits(); var learning = new AiMatchLearning(true, habits);
        var planner = new OffensePlanner(new Random(0), habits, learning);
        var difficulty = new AiDifficulty(100, 100);
        planner.begin(AiObservation.capture(game, true), 0, difficulty);
        planner.update(AiObservation.capture(game, true), new ControlInput(), 0, difficulty);
        check(planner.secondChoice() == OffensePlanner.SecondChoice.UNDECIDED, "沒有合法窗口不能先抽樣");
    }
    private static void testRecognition() {
        int[] recognized = new int[3];
        for (int index = 0; index < 3; index++) {
            int ability = new int[]{60, 80, 100}[index];
            for (int seed = 0; seed < 200; seed++) {
                var defense = new DefensePlanner(new Random(seed * 7919L));
                var stats = new AiMatchStatistics();
                var read = new AiMatchLearning.Reading(1, Setting.AIR, 3, 10, 5, 10, 20);
                var difficulty = new AiDifficulty(ability, 60);
                defense.recognize(read, 0, difficulty, stats);
                check(stats.recognitions().isEmpty(), "公開動作仍有反應延遲");
                for (int t = 1; t < 80; t++) defense.recognize(read, t, difficulty, stats);
                recognized[index] += stats.recognitions().getOrDefault(Setting.AIR, 0);
                check(stats.multiRecognitions() <= 1, "同一組合不可每幀重抽至成功");
                check(read.tempo(0) == Tempo.FIRST && read.tempo(3) == Tempo.SECOND, "公開起跳時機分類");
            }
        }
        check(recognized[0] == 0 && recognized[1] > 70 && recognized[2] > recognized[1] && recognized[2] < 200,
                "困難開始辨識、專家更準、仍會受騙");
    }

    private static void testLearningChangesActualChoices() {
        var game = firstBall(true);
        game.recordRegularHit(true, game.redTeam.wingSpiker);
        game.ball.x = 590; game.ball.y = 420;
        var o = AiObservation.capture(game, true);
        var trained = new AiMatchStatistics();
        for (int i = 0; i < 100; i++) {
            trained.attempt(KEY, CONTEXT);
            trained.result(KEY, CONTEXT, Outcome.DIRECT_POINT, PointReason.LANDING, Zone.DEEP, null);
        }
        boolean changed = false;
        for (int seed = 0; seed < 2000; seed++) {
            int normal = AttackOptions.choose(o, 1, new Random(seed * 7919L), 2, false, new AiMatchStatistics(), CONTEXT);
            int learned = AttackOptions.choose(o, 1, new Random(seed * 7919L), 2, false, trained, CONTEXT);
            changed |= normal != learned;
            int low = AttackOptions.choose(o, AiDifficulty.ability(60), new Random(seed), 2, false, trained, CONTEXT);
            int lowBaseline = AttackOptions.choose(o, AiDifficulty.ability(60), new Random(seed), 2, false, new AiMatchStatistics(), CONTEXT);
            check(low == lowBaseline, "普通即使有資料也不使用自身得分學習");
        }
        check(changed, "足夠樣本必須影響實際球路選擇，而非只改統計數字");
    }

    private static void testSecondChoiceAfterOtherReceivers() {
        for (boolean red : new boolean[]{true, false}) {
            int sets = 0, attacks = 0;
            for (int seed = 0; seed < 120; seed++) {
                var game = new GameModel();
                game.getServeHandler().setRedServing(!red);
                game.getServeHandler().setWaitingForServe(false);
                var team = red ? game.redTeam : game.blueTeam;
                game.recordRegularHit(red, team.backPlayer);
                game.ball.x = red ? 550 : 650; game.ball.y = 330; game.ball.vx = 0; game.ball.vy = -3;
                var habits = new OpponentHabits(); var learning = new AiMatchLearning(red, habits);
                var planner = new OffensePlanner(new Random(seed * 7919L), habits, learning);
                var difficulty = new AiDifficulty(100, 100);
                planner.begin(AiObservation.capture(game, red), 0, difficulty);
                for (int t = 0; t < 100 && !game.isRallyOverForNetwork(); t++) {
                    var input = new ControlInput(); planner.update(AiObservation.capture(game, red), input, t, difficulty);
                    var world = red ? input : input.mirroredHorizontally();
                    game.update(red ? world : new ControlInput(), red ? new ControlInput() : world);
                    for (var e : game.contactEvents()) if (e.redSide() == red && e.touch() == 2) {
                        check(e.role() != 0, "後排接第一球後必須跳過本人");
                        if (e.kind() == AIR_SET) sets++;
                        if (e.kind() == ATTACK) attacks++;
                    }
                    if (game.getHitCount(red) >= 2) break;
                }
            }
            check(sets > 0 && attacks > 0, "後排接第一球後也能由 WS 主動舉球或二次扣球，非僅 Setter 第一球備援");
        }
    }

    private static void testRealMatchJournal() {
        var game = new GameModel();
        var red = new RuleBasedAi(true, new AiDifficulty(100, 100), new Random(210));
        var blue = new RuleBasedAi(false, new AiDifficulty(100, 100), new Random(211));
        Map<Key, Integer> expected = new HashMap<>();
        int redBlocks = 0;
        for (int tick = 0; tick < 5000 && !game.matchOver; tick++) {
            game.update(red.update(AiObservation.capture(game, true)), blue.update(AiObservation.capture(game, false)));
            for (var e : game.contactEvents()) {
                if (e.redSide() && e.kind() == BLOCK) redBlocks++;
                boolean serve = e.kind() == SERVE;
                if (serve || e.kind() == ATTACK || e.kind() == REGULAR && e.touch() == 3) {
                    expected.merge(new Key(e.redSide(), e.role(), serve ? 0 : e.touch(), e.route(), serve), 1, Integer::sum);
                }
            }
        }
        red.update(AiObservation.capture(game, true)); blue.update(AiObservation.capture(game, false));
        check(!expected.isEmpty(), "真實對局應有實際出手");
        check(red.statistics().blocks().contacts() == redBlocks, "真實攔網事件統計一致");
        check(red.statistics().shots().size() == expected.size(), "對局中的所有實際球路都有紀錄");
        for (var entry : expected.entrySet()) {
            var key = entry.getKey();
            var otherView = new Key(!key.own(), key.role(), key.touch(), key.route(), key.serve());
            check(red.statistics().shots().get(key).attempts() == entry.getValue()
                            && blue.statistics().shots().get(otherView).attempts() == entry.getValue(),
                    "兩側統計必須對應同一份已確認模型觸球");
        }
        red.reset();
        check(red.statistics().shots().isEmpty() && red.statistics().blocks().contacts() == 0,
                "R 同時清空真實對局的出手與攔網資料");
    }
}
