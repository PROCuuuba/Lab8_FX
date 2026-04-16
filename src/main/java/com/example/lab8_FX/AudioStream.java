package com.example.lab8_FX;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class AudioStream {

    private final int localUdpPort;
    private final String remoteIp;
    private final int remoteUdpPort;

    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private DatagramSocket udpSocket;

    private volatile boolean running = false;

    private volatile boolean pushToTalk = false;

    private Thread sendThread;
    private Thread receiveThread;

    public AudioStream(int localUdpPort, String remoteIp, int remoteUdpPort) {
        this.localUdpPort = localUdpPort;
        this.remoteIp = remoteIp;
        this.remoteUdpPort = remoteUdpPort;
    }

    public void start() {
        running = true;

        AudioFormat format = new AudioFormat(
                Config.AUDIO_SAMPLE_RATE,
                Config.AUDIO_SAMPLE_SIZE,
                Config.AUDIO_CHANNELS,
                true,
                false
        );

        try {
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphone.open(format);
            microphone.start();

            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
            speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speaker.open(format);
            speaker.start();

            udpSocket = new DatagramSocket(localUdpPort);

            sendThread = new Thread(this::sendAudio);
            sendThread.start();

            receiveThread = new Thread(this::receiveAudio);
            receiveThread.start();

        } catch (LineUnavailableException | IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;

        if (sendThread != null) sendThread.interrupt();
        if (receiveThread != null) receiveThread.interrupt();

        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }

        if (speaker != null) {
            speaker.stop();
            speaker.close();
        }

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }

    public void setPushToTalk(boolean enabled) {
        this.pushToTalk = enabled;
    }

    private void sendAudio() {
        byte[] buffer = new byte[Config.AUDIO_BUFFER_SIZE];

        try {
            InetAddress address = InetAddress.getByName(remoteIp);

            while (running) {
                if (!pushToTalk) {
                    Thread.sleep(10);
                    continue;
                }

                int bytesRead = microphone.read(buffer, 0, buffer.length);

                if (bytesRead > 0) {
                    DatagramPacket packet = new DatagramPacket(
                            buffer,
                            bytesRead,
                            address,
                            remoteUdpPort
                    );
                    udpSocket.send(packet);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void receiveAudio() {
        byte[] buffer = new byte[Config.AUDIO_BUFFER_SIZE];

        try {
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                speaker.write(packet.getData(), 0, packet.getLength());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}