/*
提供畫面層讀取 UDP Client 的連線、重設與球畫面校正狀態。
NetworkStatusRenderer 與 GameRenderer 只讀取這個介面，不介入遊戲規則或封包處理。
*/
package network;

public interface NetworkView extends AutoCloseable {
    boolean isBluePerspective();

    String getHeaderText();

    String getConnectionMessage();

    String getResetMessage();

    boolean isConnected();

    boolean isSessionEnded();

    /** 非網路模式維持權威球座標；網路模式可只對畫面加入短暫校正偏移。 */
    default double getRenderedBallX(double authoritativeX) {
        return authoritativeX;
    }

    default double getRenderedBallY(double authoritativeY) {
        return authoritativeY;
    }

    default double getRenderedBallRotation(double authoritativeRotationDegrees) {
        return authoritativeRotationDegrees;
    }

    @Override
    void close();
}
