/*
管理球軌跡、落地煙霧等視覺特效的生命週期。
目前只提供產生與更新特效的入口，尚未主動接入完整特效邏輯。
*/
package model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EffectManager {
    private final List<VisualEffect> effects = new ArrayList<>();

    public List<VisualEffect> getEffects() {
        return effects;
    }

    public void spawnBallTrail(double x, double y) {
        effects.add(new VisualEffect("ball trail.png", x, y, 12));
    }

    public void spawnLandingSmoke(double x, double y) {
        // Image-based smoke removed; use SpikeEffect procedural smoke instead if needed.
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
