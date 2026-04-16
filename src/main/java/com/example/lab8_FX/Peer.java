package com.example.lab8_FX;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class Peer {

    private final int tcpPort;
    private final int udpPort;
    private final String nickname;

    private final Consumer<String> statusCallback;
    private final Consumer<String> peerListCallback;

    private LanDiscovery discovery;

    private ServerSocket serverSocket;
    private Socket activeCallSocket;

    private PrintWriter tcpOut;
    private BufferedReader tcpIn;

    private AudioStream audioStream;

    private boolean inCall = false;

    private String lastCalledPeer;
    private int retryCount = 0;
    private Thread retryThread;

    public Peer(String nickname, int tcpPort, int udpPort,
                Consumer<String> statusCallback,
                Consumer<String> peerListCallback) {

        this.nickname = nickname;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.statusCallback = statusCallback;
        this.peerListCallback = peerListCallback;
    }

    public void start() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(tcpPort);
                statusCallback.accept("Ожидание звонков на порту " + tcpPort);

                while (true) {
                    Socket socket = serverSocket.accept();
                    handleIncomingCall(socket);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        discovery = new LanDiscovery(nickname, tcpPort);
        discovery.start();

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                    updatePeerList();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void updatePeerList() {
        StringBuilder sb = new StringBuilder();

        for (LanDiscovery.PeerInfo peer : discovery.getDiscoveredPeers().values()) {
            sb.append(peer.getNickname())
                    .append("|")
                    .append(peer.getIp())
                    .append("|")
                    .append(peer.getTcpPort())
                    .append("\n");
        }

        peerListCallback.accept(sb.toString());
    }

    private void handleIncomingCall(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            String command = in.readLine();

            if (command != null && command.startsWith("CALL_START")) {

                String[] parts = command.split("\t");
                String callerNick = parts[1];
                int callerUdpPort = Integer.parseInt(parts[2]);

                statusCallback.accept("Входящий звонок от " + callerNick);

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println("CALL_ACCEPTED\t" + udpPort);

                startAudioStream(socket.getInetAddress().getHostAddress(), callerUdpPort);

                inCall = true;
                activeCallSocket = socket;
                tcpIn = in;
                tcpOut = out;

            } else if (command != null && command.equals("CALL_END")) {
                endCall();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startCall(String remoteIp, int remoteTcpPort) {
        lastCalledPeer = remoteIp + ":" + remoteTcpPort;
        retryCount = 0;
        attemptCall(remoteIp, remoteTcpPort);
    }

    private void attemptCall(String remoteIp, int remoteTcpPort) {

        new Thread(() -> {
            try {
                statusCallback.accept("Звонок на " + remoteIp + ":" + remoteTcpPort);

                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(remoteIp, remoteTcpPort), 5000);

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println("CALL_START\t" + nickname + "\t" + udpPort);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                String response = in.readLine();

                if (response != null && response.startsWith("CALL_ACCEPTED")) {

                    String[] parts = response.split("\t");
                    int remoteUdpPort = Integer.parseInt(parts[1]);

                    statusCallback.accept("Звонок принят");

                    startAudioStream(remoteIp, remoteUdpPort);

                    inCall = true;
                    activeCallSocket = socket;
                    tcpIn = in;
                    tcpOut = out;

                    retryCount = 0;

                } else {
                    handleCallFailed();
                }

            } catch (IOException e) {
                handleCallFailed();
            }
        }).start();
    }

    private void handleCallFailed() {

        statusCallback.accept("Не удалось дозвониться");

        if (retryCount < Config.MAX_RETRY_COUNT) {

            retryCount++;

            statusCallback.accept(
                    "Повтор " + retryCount + " из " + Config.MAX_RETRY_COUNT
            );

            if (retryThread != null) {
                retryThread.interrupt();
            }

            retryThread = new Thread(() -> {
                try {
                    Thread.sleep(Config.RETRY_DELAY);

                    String[] parts = lastCalledPeer.split(":");
                    attemptCall(parts[0], Integer.parseInt(parts[1]));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            retryThread.start();

        } else {
            statusCallback.accept("Все попытки исчерпаны");
            retryCount = 0;
        }
    }

    public void endCall() {

        if (inCall && tcpOut != null) {
            tcpOut.println("CALL_END");
        }

        stopAudioStream();

        inCall = false;

        if (activeCallSocket != null) {
            try {
                activeCallSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        statusCallback.accept("Звонок завершен");
    }

    private void startAudioStream(String remoteIp, int remoteUdpPort) {
        audioStream = new AudioStream(udpPort, remoteIp, remoteUdpPort);
        audioStream.start();
    }

    private void stopAudioStream() {
        if (audioStream != null) {
            audioStream.stop();
            audioStream = null;
        }
    }

    public void setPushToTalk(boolean enabled) {
        if (audioStream != null) {
            audioStream.setPushToTalk(enabled);
        }
    }

    public void stop() {

        if (discovery != null) {
            discovery.stop();
        }

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        endCall();
    }
}