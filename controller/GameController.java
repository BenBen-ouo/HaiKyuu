/*
控制層入口，將鍵盤輸入送往單機 GameModel 或 UDP Client。
連線模式下 Client 選取自己的隊伍輸入並執行本地預測，Server 不再由畫面程序持有。
*/
package controller;

import model.GameModel;
import network.GameClient;

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
        if (client != null) {
            client.update(
                    keyboard.getRedInput(),
                    keyboard.getBlueInput(),
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
