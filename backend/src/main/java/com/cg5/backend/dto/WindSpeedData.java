package com.cg5.backend.dto;

public class WindSpeedData {
    private String deviceId;
    private Double windSpeed;
    private Double windDirection;
    private Long timestamp;

    public WindSpeedData() {
    }

    public WindSpeedData(String deviceId, Double windSpeed, Double windDirection, Long timestamp) {
        this.deviceId = deviceId;
        this.windSpeed = windSpeed;
        this.windDirection = windDirection;
        this.timestamp = timestamp;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Double getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(Double windSpeed) {
        this.windSpeed = windSpeed;
    }

    public Double getWindDirection() {
        return windDirection;
    }

    public void setWindDirection(Double windDirection) {
        this.windDirection = windDirection;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
