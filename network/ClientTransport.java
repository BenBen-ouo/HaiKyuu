package network;

import java.io.IOException;

/** Client 的資料報邊界；傳輸擁有接收資源，關閉必須解除阻塞中的 receive。 */
interface ClientTransport extends AutoCloseable {
    void send(byte[] data) throws IOException;

    UdpCodec.Decoded receive() throws IOException;

    boolean isClosed();

    @Override
    void close();
}
