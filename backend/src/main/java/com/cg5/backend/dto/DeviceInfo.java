package com.cg5.backend.dto;

public class DeviceInfo {
    private String deviceId;
    private Boolean online;
    private Long connectTime;
    private Double currentAngle;

    public DeviceInfo() {
    }

    public DeviceInfo(String deviceId, Boolean online, Long connectTime) {
        this.deviceId = deviceId;
        this.online = online;
        this.connectTime = connectTime;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Boolean getOnline() {
        return online;
    }

    public void setOnline(Boolean online) {
        this.online = online;
    }

    public Long getConnectTime() {
        return connectTime;
    }

    public void setConnectTime(Long connectTime) {
        this.connectTime = connectTime;
    }

    public Double getCurrentAngle() {
        return currentAngle;
    }

    public void setCurrentAngle(Double currentAngle) {
        this.currentAngle = currentAngle;
    }
}
