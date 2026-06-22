/*
負責監聽鍵盤按下與放開，並轉換成單機或區域網路模式可用的 TeamInput。
區域網路 Player 2 也使用 WASD、JKL、Space；其畫面鏡像後會轉換成藍隊的世界座標方向。
*/
package controller;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;
import model.ServeType;
import model.TeamInput;

public class KeyboardController implements KeyListener {
    private final Set<Integer> pressedKeys = new HashSet<>();

    public synchronized TeamInput getRedInput() {
        TeamInput input = new TeamInput();

        input.backLeft = isPressed(KeyEvent.VK_A);
        input.backRight = isPressed(KeyEvent.VK_D);
        input.backJump = isPressed(KeyEvent.VK_SPACE);
        input.backDive = isPressed(KeyEvent.VK_SPACE);
        input.setterJump = isPressed(KeyEvent.VK_K);
        input.quickAttack = isPressed(KeyEvent.VK_L);
        input.quickBlock = isPressed(KeyEvent.VK_L);
        input.wingAttack = isPressed(KeyEvent.VK_J);
        input.spikeFlat = isPressed(KeyEvent.VK_D);
        input.spikeShort = isPressed(KeyEvent.VK_S);
        input.spikeLob = isPressed(KeyEvent.VK_W);
        input.servePressed = isPressed(KeyEvent.VK_SPACE);
        input.serveType = getServeType();

        return input;
    }

    /*
     * 單機測試保留的藍隊方向鍵／數字鍵控制。
     * 區域網路模式不使用此方法，Player 2 改用 getMirroredBlueInput()。
     */
    public synchronized TeamInput getBlueInput() {
        TeamInput input = new TeamInput();

        input.backLeft = isPressed(KeyEvent.VK_LEFT);
        input.backRight = isPressed(KeyEvent.VK_RIGHT);
        input.backJump = isPressed(KeyEvent.VK_NUMPAD0);
        input.backDive = isPressed(KeyEvent.VK_NUMPAD0);
        input.setterJump = isPressed(KeyEvent.VK_NUMPAD5);
        input.quickAttack = isPressed(KeyEvent.VK_NUMPAD6);
        input.quickBlock = isPressed(KeyEvent.VK_NUMPAD6);
        input.wingAttack = isPressed(KeyEvent.VK_NUMPAD4);
        input.spikeFlat = isPressed(KeyEvent.VK_RIGHT);
        input.spikeShort = isPressed(KeyEvent.VK_DOWN);
        input.spikeLob = isPressed(KeyEvent.VK_UP);
        input.servePressed = isPressed(KeyEvent.VK_NUMPAD0);
        input.serveType = getBlueServeType();

        return input;
    }

    /*
     * Player 2 的本機畫面已水平鏡像：
     * A = 畫面左方 = 真實世界右方（遠離網子）
     * D = 畫面右方 = 真實世界左方（朝向網子）
     * 其餘功能鍵與 Player 1 相同，皆為 WASD、JKL、Space。
     */
    public synchronized TeamInput getMirroredBlueInput() {
        TeamInput input = new TeamInput();

        input.backLeft = isPressed(KeyEvent.VK_D);
        input.backRight = isPressed(KeyEvent.VK_A);
        input.backJump = isPressed(KeyEvent.VK_SPACE);
        input.backDive = isPressed(KeyEvent.VK_SPACE);
        input.setterJump = isPressed(KeyEvent.VK_K);
        input.quickAttack = isPressed(KeyEvent.VK_L);
        input.quickBlock = isPressed(KeyEvent.VK_L);
        input.wingAttack = isPressed(KeyEvent.VK_J);
        input.spikeFlat = isPressed(KeyEvent.VK_D);
        input.spikeShort = isPressed(KeyEvent.VK_S);
        input.spikeLob = isPressed(KeyEvent.VK_W);
        input.servePressed = isPressed(KeyEvent.VK_SPACE);
        input.serveType = getServeType();

        return input;
    }

    public synchronized boolean isRestartPressed() {
        return isPressed(KeyEvent.VK_R);
    }

    public synchronized boolean isCancelResetPressed() {
        return isPressed(KeyEvent.VK_N);
    }

    private ServeType getServeType() {
        if (isPressed(KeyEvent.VK_W)) {
            return ServeType.CEILING;
        }
        if (isPressed(KeyEvent.VK_S)) {
            return ServeType.LOW_NET;
        }
        if (isPressed(KeyEvent.VK_A)) {
            return ServeType.SHORT;
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

        return ServeType.NORMAL;
    }

    private boolean isPressed(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    @Override
    public synchronized void keyPressed(KeyEvent event) {
        pressedKeys.add(event.getKeyCode());
    }

    @Override
    public synchronized void keyReleased(KeyEvent event) {
        pressedKeys.remove(event.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent event) {
    }
}
