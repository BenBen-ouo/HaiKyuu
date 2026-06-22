/*
提供給畫面層讀取的網路連線狀態。
主機與 Client 都實作此介面，GamePanel 只負責依狀態顯示文字與決定是否鏡像世界畫面。
*/
package network;

public interface NetworkView extends AutoCloseable {
    boolean isBluePerspective();

    String getHeaderText();

    String getConnectionMessage();

    String getResetMessage();

    boolean isConnected();

    @Override
    void close();
}
