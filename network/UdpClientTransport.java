package network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/** 固定遠端端點的 UDP 傳輸；不接受其他來源的資料報。 */
final class UdpClientTransport implements ClientTransport {
    private static final int RECEIVE_BUFFER_BYTES = 4096;

    private final DatagramSocket socket;
    private final InetSocketAddress endpoint;
    private final byte[] receiveBuffer = new byte[RECEIVE_BUFFER_BYTES];

    UdpClientTransport(String host, int port) throws IOException {
        endpoint = new InetSocketAddress(InetAddress.getByName(host), port);
        socket = new DatagramSocket();
    }

    @Override
    public void send(byte[] data) throws IOException {
        socket.send(new DatagramPacket(data, data.length, endpoint));
    }

    @Override
    public Received receive() throws IOException {
        DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(packet);
        long arrivedAt = System.nanoTime();
        UdpCodec.Decoded decoded = endpoint.equals(packet.getSocketAddress())
                ? UdpCodec.decode(packet.getData(), packet.getLength()) : null;
        TimingDiagnostics.recordIfSlow("udp decode", arrivedAt, 5);
        return new Received(decoded, arrivedAt);
    }

    @Override
    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public void close() {
        socket.close();
    }
}
