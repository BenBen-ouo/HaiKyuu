/*
底層動畫播放器，依照圖片序列與停留 frame 數切換角色 assetName。
支援一次性動畫、循環動畫與停在最後一張圖的 HOLD 動畫。
*/
package model;

public class PlayerAnimation {
    private final Player owner;
    private final String idleAssetName;

    private String[] frames = new String[0];
    private int[] durations = new int[0];
    private int frameIndex = 0;
    private int frameTimer = 0;
    private boolean playing = false;
    private boolean loop = false;

    public PlayerAnimation(Player owner, String idleAssetName) {
        this.owner = owner;
        this.idleAssetName = idleAssetName;
    }

    public void play(String[] frames, int[] durations) {
        playInternal(frames, durations, false);
    }

    public void playLoop(String[] frames, int[] durations) {
        playInternal(frames, durations, true);
    }

    private void playInternal(String[] frames, int[] durations, boolean loop) {
        if (frames == null || durations == null || frames.length == 0 || frames.length != durations.length) {
            stopToIdle();
            return;
        }

        this.frames = frames;
        this.durations = durations;
        this.frameIndex = 0;
        this.frameTimer = 0;
        this.playing = true;
        this.loop = loop;
        owner.assetName = frames[0];
    }

    public void show(String assetName) {
        playing = false;
        loop = false;
        frames = new String[0];
        durations = new int[0];
        frameIndex = 0;
        frameTimer = 0;
        owner.assetName = assetName;
    }

    public void update() {
        if (!playing) {
            return;
        }

        int duration = durations[frameIndex];

        // duration < 0 表示停在目前這張，直到外部狀態結束。
        if (duration < 0) {
            return;
        }

        frameTimer++;

        if (frameTimer < duration) {
            return;
        }

        frameTimer = 0;
        frameIndex++;

        if (frameIndex >= frames.length) {
            if (loop) {
                frameIndex = 0;
                owner.assetName = frames[frameIndex];
            } else {
                stopToIdle();
            }
            return;
        }

        owner.assetName = frames[frameIndex];
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isHoldingFrame() {
        return playing && durations.length > 0 && durations[frameIndex] < 0;
    }

    public boolean isLooping() {
        return playing && loop;
    }

    public void stopToIdle() {
        playing = false;
        loop = false;
        frames = new String[0];
        durations = new int[0];
        frameIndex = 0;
        frameTimer = 0;
        owner.assetName = idleAssetName;
    }
}