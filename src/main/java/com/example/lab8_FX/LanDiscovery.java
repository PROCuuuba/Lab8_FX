package com.example.lab8_FX;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LanDiscovery implements Runnable {

    private final String nickname;
    private final int tcpPort;
    private final Map<String, PeerInfo> discoveredPeers = new ConcurrentHashMap<>();
    private MulticastSocket multicastSocket;
    private boolean running = true;

    public LanDiscovery(String nickname, int tcpPort) {
        this.nickname = nickname;
        this.tcpPort = tcpPort;
    }

    public void start() {
        new Thread(this).start();
    }

    public void stop() {
        running = false;
        if (multicastSocket != null && !multicastSocket.isClosed()) {
            try {
                InetAddress group = InetAddress.getByName(Config.MULTICAST_ADDRESS);
                multicastSocket.leaveGroup(group);
                multicastSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        try {
            InetAddress group = InetAddress.getByName(Config.MULTICAST_ADDRESS);
            multicastSocket = new MulticastSocket(Config.MULTICAST_PORT);
            multicastSocket.joinGroup(group);
            multicastSocket.setSoTimeout(1000);

            // Поток для отправки HELLO сообщений
            new Thread(() -> {
                while (running) {
                    try {
                        String message = String.format("HELLO\t%s\t%s\t%d",
                                nickname, getLocalIp(), tcpPort);
                        DatagramPacket packet = new DatagramPacket(
                                message.getBytes(), message.length(), group, Config.MULTICAST_PORT);
                        multicastSocket.send(packet);
                        Thread.sleep(Config.HELLO_INTERVAL);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            byte[] buffer = new byte[1024];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    parseMessage(message, packet.getAddress());
                } catch (SocketTimeoutException e) {
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseMessage(String message, InetAddress senderIp) {
        String[] parts = message.split("\t");
        if (parts.length >= 4 && parts[0].equals("HELLO")) {
            String senderNick = parts[1];
            String senderIpStr = parts[2];
            int senderPort = Integer.parseInt(parts[3]);

            if (senderNick.equals(nickname) && senderIpStr.equals(getLocalIp())) {
                return;
            }

            PeerInfo peer = new PeerInfo(senderNick, senderIpStr, senderPort, System.currentTimeMillis());
            discoveredPeers.put(senderNick, peer);
        }
    }

    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    public Map<String, PeerInfo> getDiscoveredPeers() {
        long now = System.currentTimeMillis();
        discoveredPeers.entrySet().removeIf(entry ->
                now - entry.getValue().getLastSeen() > Config.HEARTBEAT_TIMEOUT);
        return discoveredPeers;
    }

    public static class PeerInfo {
        private final String nickname;
        private final String ip;
        private final int tcpPort;
        private long lastSeen;

        public PeerInfo(String nickname, String ip, int tcpPort, long lastSeen) {
            this.nickname = nickname;
            this.ip = ip;
            this.tcpPort = tcpPort;
            this.lastSeen = lastSeen;
        }

        public String getNickname() { return nickname; }
        public String getIp() { return ip; }
        public int getTcpPort() { return tcpPort; }
        public long getLastSeen() { return lastSeen; }
        public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
    }
}