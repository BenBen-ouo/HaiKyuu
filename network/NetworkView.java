/*
提供畫面層讀取 UDP Client 的連線、重設與球員／球畫面校正狀態。
NetworkStatusRenderer 與 GameRenderer 只讀取這個介面，不介入遊戲規則或封包處理。
*/
package network;

import model.player.Player;

public interface NetworkView extends AutoCloseable {
    boolean isBluePerspective();

    String getConnectionMessage();

    String getResetMessage();

    boolean isConnected();

    boolean isSessionEnded();

    double getRenderedPlayerX(Player player);

    double getRenderedPlayerY(Player player);

    String getRenderedPlayerAsset(Player player);

    boolean getRenderedPlayerMirror(Player player);

    @Override
    void close();
}
