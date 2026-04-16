package com.example.lab8_FX;

public class Config {
    public static final String MULTICAST_ADDRESS = "230.0.0.1";
    public static final int MULTICAST_PORT = 8888;

    public static final int HELLO_INTERVAL = 3000;
    public static final int HEARTBEAT_TIMEOUT = 15000;

    public static final int AUDIO_SAMPLE_RATE = 16000;
    public static final int AUDIO_SAMPLE_SIZE = 16;
    public static final int AUDIO_CHANNELS = 1;
    public static final int AUDIO_BUFFER_SIZE = 4096;

    public static final int MAX_RETRY_COUNT = 3;
    public static final int RETRY_DELAY = 10000;
}