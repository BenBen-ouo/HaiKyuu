/*
尋找可供同一區域網路其他電腦使用的 IPv4 位址。
優先回傳非 loopback、已啟用網卡上的私有或一般 IPv4；找不到時回傳 127.0.0.1。
*/
package network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public final class NetworkAddress {
    private NetworkAddress() {
    }

    public static String findLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
            // 使用下方 loopback 作為最後備援，讓同一台電腦測試仍可進行。
        }

        return "127.0.0.1";
    }
}
