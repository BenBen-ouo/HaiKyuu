/*
控制層入口，將鍵盤輸入送往單機模型或區域網路主機／Client。
主機模式只有 GameServer 更新真正 GameModel；Client 模式只傳送藍隊輸入並接收主機快照。
*/
package controller;

import model.GameModel;
import network.GameClient;
import network.GameServer;

public class GameController {
    private final GameModel model;
    private final KeyboardController keyboard;
    private final GameServer server;
    private final GameClient client;

    public GameController(GameModel model, KeyboardController keyboard) {
        this(model, keyboard, null, null);
    }

    public GameController(GameModel model, KeyboardController keyboard, GameServer server) {
        this(model, keyboard, server, null);
    }

    public GameController(GameModel model, KeyboardController keyboard, GameClient client) {
        this(model, keyboard, null, client);
    }

    private GameController(
            GameModel model,
            KeyboardController keyboard,
            GameServer server,
            GameClient client
    ) {
        this.model = model;
        this.keyboard = keyboard;
        this.server = server;
        this.client = client;
    }

    public void update() {
        if (server != null) {
            server.update(
                    keyboard.getRedInput(),
                    keyboard.isRestartPressed(),
                    keyboard.isCancelResetPressed()
            );
            return;
        }

        if (client != null) {
            client.update(
                    keyboard.getMirroredBlueInput(),
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
