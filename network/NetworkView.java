/*
 * 提供畫面層讀取的網路狀態。
 * 遊戲畫面依此決定鏡像、連線文字、重設提示，以及斷線後是否結束本局。
 */
package network;

public interface NetworkView extends AutoCloseable {
    boolean isBluePerspective();

    String getHeaderText();

    String getConnectionMessage();

    String getResetMessage();

    boolean isConnected();

    boolean isSessionEnded();

    @Override
    void close();
}
