package com.cg5.backend.dto;

public class ProtectionStatus {
    private String deviceId;
    private Boolean protectionEnabled;
    private Boolean protectionActive;
    private Boolean autoUnloadEnabled;
    private Double currentWindSpeed;
    private Double windSpeedThreshold;
    private Double unloadTargetAngle;
    private Double currentNacelleAngle;
    private String reason;
    private Long timestamp;

    public ProtectionStatus() {
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Boolean getProtectionEnabled() {
        return protectionEnabled;
    }

    public void setProtectionEnabled(Boolean protectionEnabled) {
        this.protectionEnabled = protectionEnabled;
    }

    public Boolean getProtectionActive() {
        return protectionActive;
    }

    public void setProtectionActive(Boolean protectionActive) {
        this.protectionActive = protectionActive;
    }

    public Boolean getAutoUnloadEnabled() {
        return autoUnloadEnabled;
    }

    public void setAutoUnloadEnabled(Boolean autoUnloadEnabled) {
        this.autoUnloadEnabled = autoUnloadEnabled;
    }

    public Double getCurrentWindSpeed() {
        return currentWindSpeed;
    }

    public void setCurrentWindSpeed(Double currentWindSpeed) {
        this.currentWindSpeed = currentWindSpeed;
    }

    public Double getWindSpeedThreshold() {
        return windSpeedThreshold;
    }

    public void setWindSpeedThreshold(Double windSpeedThreshold) {
        this.windSpeedThreshold = windSpeedThreshold;
    }

    public Double getUnloadTargetAngle() {
        return unloadTargetAngle;
    }

    public void setUnloadTargetAngle(Double unloadTargetAngle) {
        this.unloadTargetAngle = unloadTargetAngle;
    }

    public Double getCurrentNacelleAngle() {
        return currentNacelleAngle;
    }

    public void setCurrentNacelleAngle(Double currentNacelleAngle) {
        this.currentNacelleAngle = currentNacelleAngle;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
