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