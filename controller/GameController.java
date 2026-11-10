/*
控制層入口，將鍵盤輸入送往單機 GameModel 或 UDP Client。
連線模式下兩位 Client 都讀取 WASD 操作；Player 2 的世界方向由 GameClient 轉換。
*/
package controller;

import model.GameModel;
import model.ControlInput;
import model.ai.AiObservation;
import model.ai.RuleBasedAi;
import network.GameClient;
import view.DebugSettings;

public class GameController {
    private final GameModel model;
    private final KeyboardController keyboard;
    private final GameClient client;
    private final RuleBasedAi ai;
    private boolean paused;

    public boolean canPause() { return client == null; }
    public boolean isAiMatch() { return ai != null; }
    public boolean isAiRedSide() { return ai != null && ai.isRedSide(); }

    public synchronized boolean isPaused() { return paused; }

    public synchronized void setPaused(boolean value) {
        if (!canPause()) throw new IllegalStateException("Network matches cannot be paused locally");
        if (paused == value) return;
        keyboard.setSuspended(value);
        paused = value;
    }

    public GameController(GameModel model, KeyboardController keyboard) {
        this(model, keyboard, null);
    }

    public GameController(GameModel model, KeyboardController keyboard, GameClient client) {
        this(model, keyboard, client, null);
    }

    public GameController(GameModel model, KeyboardController keyboard, GameClient client, RuleBasedAi ai) {
        if (client != null && ai != null) throw new IllegalArgumentException("AI is local only");
        this.model = model;
        this.keyboard = keyboard;
        this.client = client;
        this.ai = ai;
    }

    public synchronized void update() {
        if (paused) return;
        if (keyboard.consumeHitBoxTogglePressed()) {
            DebugSettings.toggleHitBoxesVisible();
        }

        if (client != null) {
            client.update(
                    keyboard.getRedInput(),
                    keyboard.isRestartPressed(),
                    keyboard.isCancelResetPressed()
            );
            return;
        }

        if (keyboard.isRestartPressed()) {
            model.restart();
            if (ai != null) ai.reset();
        }
        ControlInput red = keyboard.getRedInput();
        ControlInput blue = keyboard.getBlueInput();
        if (ai != null) {
            ControlInput bot = ai.update(AiObservation.capture(model, ai.isRedSide()));
            if (ai.isRedSide()) red = bot;
            else blue = bot;
        }
        model.update(red, blue);
    }
}
