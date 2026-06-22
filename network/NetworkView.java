/*
提供畫面層讀取 UDP Client 的連線與重設狀態。
NetworkStatusRenderer 只讀取這個介面，不介入遊戲規則或封包處理。
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
