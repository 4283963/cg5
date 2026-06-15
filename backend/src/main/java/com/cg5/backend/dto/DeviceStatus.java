package com.cg5.backend.dto;

public class DeviceStatus {
    private String deviceId;
    private Double currentAngle;
    private Double targetAngle;
    private Boolean online;
    private Long lastUpdateTime;

    public DeviceStatus() {
    }

    public DeviceStatus(String deviceId, Double currentAngle, Double targetAngle, Boolean online, Long lastUpdateTime) {
        this.deviceId = deviceId;
        this.currentAngle = currentAngle;
        this.targetAngle = targetAngle;
        this.online = online;
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Double getCurrentAngle() {
        return currentAngle;
    }

    public void setCurrentAngle(Double currentAngle) {
        this.currentAngle = currentAngle;
    }

    public Double getTargetAngle() {
        return targetAngle;
    }

    public void setTargetAngle(Double targetAngle) {
        this.targetAngle = targetAngle;
    }

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    public Long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
}
