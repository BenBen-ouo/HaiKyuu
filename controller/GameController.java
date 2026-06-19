/*
控制層入口，負責把鍵盤輸入轉交給 GameModel 更新遊戲狀態。
也在每幀檢查重新開始按鍵，觸發整場遊戲重置。
*/
package controller;

import model.GameModel;

public class GameController {
    private final GameModel model;
    private final KeyboardController keyboard;

    public GameController(GameModel model, KeyboardController keyboard) {
        this.model = model;
        this.keyboard = keyboard;
    }

    public void update() {
        if (keyboard.isRestartPressed()) {
            model.restart();
        }
        model.update(keyboard.getRedInput(), keyboard.getBlueInput());
    }
}