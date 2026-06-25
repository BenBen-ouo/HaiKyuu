/*
負責監聽鍵盤按下與放開，並轉換成紅隊與藍隊的 TeamInput。
所有角色操作鍵、發球鍵與發球類型都從這裡整理成模型可讀的資料。
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
    private boolean previousHitBoxTogglePressed;

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
        input.serveType = getRedServeType();

        return input;
    }

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

        // 沒有獨立數字鍵測試用 之後會刪除
        // input.backJump = isPressed(KeyEvent.VK_0);
        // input.backDive = isPressed(KeyEvent.VK_0);
        // input.setterJump = isPressed(KeyEvent.VK_8);
        // input.quickAttack = isPressed(KeyEvent.VK_9);
        // input.quickBlock = isPressed(KeyEvent.VK_9);
        // input.wingAttack = isPressed(KeyEvent.VK_7);
        // input.servePressed = isPressed(KeyEvent.VK_0);
        
        input.serveType = getBlueServeType();

        return input;
    }

    public synchronized boolean isRestartPressed() {
        return isPressed(KeyEvent.VK_R);
    }

    public synchronized boolean isCancelResetPressed() {
        return isPressed(KeyEvent.VK_N);
    }

    /** F3 僅切換測試畫面的碰撞箱顯示，不會送入遊戲輸入或網路封包。 */
    public synchronized boolean consumeHitBoxTogglePressed() {
        boolean currentlyPressed = isPressed(KeyEvent.VK_F3);
        boolean justPressed = currentlyPressed && !previousHitBoxTogglePressed;
        previousHitBoxTogglePressed = currentlyPressed;
        return justPressed;
    }

    private synchronized ServeType getRedServeType() {
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

    private synchronized ServeType getBlueServeType() {
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

    private synchronized boolean isPressed(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    @Override
    public synchronized void keyPressed(KeyEvent e) {
        pressedKeys.add(e.getKeyCode());
    }

    @Override
    public synchronized void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) {}
}