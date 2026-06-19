/*
集中管理共用動畫序列，例如跑步循環、圖片檔名組合與每張圖停留時間。
讓角色動畫流程不用在各個 Player 類別中重複寫 frame 與 duration。
*/
package model;

public final class AnimationSequences {
    public static final int HOLD = -1;
    private static final int RUN_FRAME_DURATION = 2;

    private AnimationSequences() {}

    public static void playRunCycles(PlayerAnimation animation, Player player, int cycles) {
        int safeCycles = Math.max(1, cycles);
        String[] cycleFrames = runFrames(player);
        int[] cycleDurations = runDurations();
        String[] frames = new String[cycleFrames.length * safeCycles];
        int[] durations = new int[cycleDurations.length * safeCycles];

        for (int cycle = 0; cycle < safeCycles; cycle++) {
            copyCycle(cycle, cycleFrames, cycleDurations, frames, durations);
        }

        animation.play(frames, durations);
    }

    public static void playRunLoop(PlayerAnimation animation, Player player) {
        animation.playLoop(runFrames(player), runDurations());
    }

    public static String[] frames(Player player, String... actionNames) {
        String[] assetNames = new String[actionNames.length];

        for (int i = 0; i < actionNames.length; i++) {
            assetNames[i] = player.teamAsset(actionNames[i]);
        }

        return assetNames;
    }

    public static int[] durations(int... frameDurations) {
        return frameDurations;
    }

    private static String[] runFrames(Player player) {
        return frames(player, "run1", "run2", "run3", "run2");
    }

    private static int[] runDurations() {
        return durations(RUN_FRAME_DURATION, RUN_FRAME_DURATION, RUN_FRAME_DURATION, RUN_FRAME_DURATION);
    }

    private static void copyCycle(int cycle, String[] sourceFrames, int[] sourceDurations,
                                  String[] targetFrames, int[] targetDurations) {
        for (int i = 0; i < sourceFrames.length; i++) {
            int index = cycle * sourceFrames.length + i;
            targetFrames[index] = sourceFrames[i];
            targetDurations[index] = sourceDurations[i];
        }
    }
}