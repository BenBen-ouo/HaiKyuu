package controller;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;
import model.TeamInput;

public class KeyboardController implements KeyListener {
    private final Set<Integer> pressedKeys = new HashSet<>();

    public TeamInput getRedInput() {
        TeamInput input = new TeamInput();

        input.backLeft = isPressed(KeyEvent.VK_A);
        input.backRight = isPressed(KeyEvent.VK_D);
        input.backJump = isPressed(KeyEvent.VK_SPACE);
        input.backDive = isPressed(KeyEvent.VK_SPACE);
        input.setterJump = isPressed(KeyEvent.VK_K);
        input.quickAttack = isPressed(KeyEvent.VK_L);
        input.quickBlock = isPressed(KeyEvent.VK_L);
        input.wingAttack = isPressed(KeyEvent.VK_J);
        input.servePressed = isPressed(KeyEvent.VK_SPACE);
        input.serveType = getRedServeType();

        return input;
    }

    public TeamInput getBlueInput() {
        TeamInput input = new TeamInput();

        input.backLeft = isPressed(KeyEvent.VK_LEFT);
        input.backRight = isPressed(KeyEvent.VK_RIGHT);
        input.backJump = isPressed(KeyEvent.VK_NUMPAD0);
        input.backDive = isPressed(KeyEvent.VK_NUMPAD0);
        input.setterJump = isPressed(KeyEvent.VK_NUMPAD5);
        input.quickAttack = isPressed(KeyEvent.VK_NUMPAD6);
        input.quickBlock = isPressed(KeyEvent.VK_NUMPAD6);
        input.wingAttack = isPressed(KeyEvent.VK_NUMPAD4);

        return input;
    }

    private int getRedServeType() {
        if (isPressed(KeyEvent.VK_W)) {
            return TeamInput.SERVE_CEILING;
        }
        if (isPressed(KeyEvent.VK_S)) {
            return TeamInput.SERVE_LOW_NET;
        }
        if (isPressed(KeyEvent.VK_A)) {
            return TeamInput.SERVE_SHORT;
        }
        if (isPressed(KeyEvent.VK_D)) {
            return TeamInput.SERVE_JUMP;
        }
        return TeamInput.SERVE_NORMAL;
    }

    // private int getBlueServeType() {
    //     if (isPressed(KeyEvent.VK_UP)) {
    //         return TeamInput.SERVE_CEILING;
    //     }
    //     if (isPressed(KeyEvent.VK_DOWN)) {
    //         return TeamInput.SERVE_LOW_NET;
    //     }
    //     if (isPressed(KeyEvent.VK_LEFT)) {
    //         return TeamInput.SERVE_SHORT;
    //     }
    //     if (isPressed(KeyEvent.VK_RIGHT)) {
    //         return TeamInput.SERVE_JUMP;
    //     }
    //     return TeamInput.SERVE_NORMAL;
    // }

    private boolean isPressed(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        pressedKeys.add(e.getKeyCode());
    }

    @Override
    public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) {}
}