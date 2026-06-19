package controller;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;
import model.ServeType;
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
        input.servePressed = isPressed(KeyEvent.VK_NUMPAD0);
        input.serveType = getBlueServeType();

        return input;
    }

    public boolean isRestartPressed() {
        return isPressed(KeyEvent.VK_R);
    }

    private ServeType getRedServeType() {
        if (isPressed(KeyEvent.VK_W)) {
            return ServeType.CEILING;
        }
        if (isPressed(KeyEvent.VK_S)) {
            return ServeType.LOW_NET;
        }
        if (isPressed(KeyEvent.VK_A)) {
            return ServeType.SHORT;
        }
        if (isPressed(KeyEvent.VK_D)) {
            return ServeType.JUMP;
        }
        return ServeType.NORMAL;
    }

    private ServeType getBlueServeType() {
        if (isPressed(KeyEvent.VK_UP)) {
            return ServeType.CEILING;
        }
        if (isPressed(KeyEvent.VK_DOWN)) {
            return ServeType.LOW_NET;
        }
        if (isPressed(KeyEvent.VK_RIGHT)) {
            return ServeType.SHORT;
        }
        if (isPressed(KeyEvent.VK_LEFT)) {
            return ServeType.JUMP;
        }
        return ServeType.NORMAL;
    }

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