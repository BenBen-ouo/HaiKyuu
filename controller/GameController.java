/*
控制層入口，將鍵盤輸入送往單機 GameModel 或 UDP Client。
連線模式下兩位 Client 都讀取 WASD 操作；Player 2 的世界方向由 GameClient 轉換。
*/
package controller;

import model.GameModel;
import network.GameClient;
import view.DebugSettings;

public class GameController {
    private final GameModel model;
    private final KeyboardController keyboard;
    private final GameClient client;

    public GameController(GameModel model, KeyboardController keyboard) {
        this(model, keyboard, null);
    }

    public GameController(GameModel model, KeyboardController keyboard, GameClient client) {
        this.model = model;
        this.keyboard = keyboard;
        this.client = client;
    }

    public void update() {
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
        }
        model.update(keyboard.getRedInput(), keyboard.getBlueInput());
    }
}
