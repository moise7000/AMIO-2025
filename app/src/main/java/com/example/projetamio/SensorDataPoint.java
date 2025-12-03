package com.example.projetamio;

public class SensorDataPoint {
    private final long timestamp;
    private final float value;

    public SensorDataPoint(long timestamp, float value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getValue() {
        return value;
    }
}