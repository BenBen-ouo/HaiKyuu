package model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// 預留給球軌跡、落地煙霧等視覺特效使用。
// 目前只管理生命週期，尚未在遊戲邏輯中主動產生特效。

public class EffectManager {
    private final List<VisualEffect> effects = new ArrayList<>();

    public List<VisualEffect> getEffects() {
        return effects;
    }

    public void spawnBallTrail(double x, double y) {
        effects.add(new VisualEffect("ball trail.png", x, y, 12));
    }

    public void spawnLandingSmoke(double x, double y) {
        effects.add(new VisualEffect("smoke.png", x, y, 20));
    }

    public void update() {
        Iterator<VisualEffect> iterator = effects.iterator();

        while (iterator.hasNext()) {
            VisualEffect effect = iterator.next();
            effect.update();

            if (!effect.isAlive()) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        effects.clear();
    }
}
