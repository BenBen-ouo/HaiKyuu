package model.effect;

/** 已確認攔網觸球的短暫提示；不影響觸球次數、球路或軌跡。 */
public final class BlockTouchEffect {
    public static final int DURATION_FRAMES = 30;
    private int redFrames;
    private int blueFrames;
    private boolean redContactThisFrame;
    private boolean blueContactThisFrame;

    public void beginFrame() {
        if (redFrames > 0) redFrames--;
        if (blueFrames > 0) blueFrames--;
        redContactThisFrame = false;
        blueContactThisFrame = false;
    }

    public void show(boolean redSide) {
        if (redSide) { redFrames = DURATION_FRAMES; redContactThisFrame = true; }
        else { blueFrames = DURATION_FRAMES; blueContactThisFrame = true; }
    }

    public boolean isVisible(boolean redSide) { return (redSide ? redFrames : blueFrames) > 0; }
    public boolean didContactThisFrame(boolean redSide) { return redSide ? redContactThisFrame : blueContactThisFrame; }

    public void clear() {
        redFrames = 0;
        blueFrames = 0;
        redContactThisFrame = false;
        blueContactThisFrame = false;
    }
}
