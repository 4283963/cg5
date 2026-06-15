package com.cg5.backend.dto;

public class WebSocketMessage {
    private String type;
    private String deviceId;
    private Object data;
    private Long timestamp;

    private Double targetAngle;
    private Double currentAngle;
    private Double shortestDiff;
    private Integer rotationDirection;
    private Double rotationDegrees;

    private Double windSpeed;
    private Double windDirection;
    private Boolean protectionEnabled;
    private Boolean protectionActive;
    private Boolean autoUnloadEnabled;
    private Double unloadAngleOffset;
    private Double windSpeedThreshold;
    private Double unloadTargetAngle;
    private Double currentNacelleAngle;
    private String reason;

    public WebSocketMessage() {
    }

    public WebSocketMessage(String type, String deviceId, Object data, Long timestamp) {
        this.type = type;
        this.deviceId = deviceId;
        this.data = data;
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Double getTargetAngle() {
        return targetAngle;
    }

    public void setTargetAngle(Double targetAngle) {
        this.targetAngle = targetAngle;
    }

    public Double getCurrentAngle() {
        return currentAngle;
    }

    public void setCurrentAngle(Double currentAngle) {
        this.currentAngle = currentAngle;
    }

    public Double getShortestDiff() {
        return shortestDiff;
    }

    public void setShortestDiff(Double shortestDiff) {
        this.shortestDiff = shortestDiff;
    }

    public Integer getRotationDirection() {
        return rotationDirection;
    }

    public void setRotationDirection(Integer rotationDirection) {
        this.rotationDirection = rotationDirection;
    }

    public Double getRotationDegrees() {
        return rotationDegrees;
    }

    public void setRotationDegrees(Double rotationDegrees) {
        this.rotationDegrees = rotationDegrees;
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

    public Double getUnloadAngleOffset() {
        return unloadAngleOffset;
    }

    public void setUnloadAngleOffset(Double unloadAngleOffset) {
        this.unloadAngleOffset = unloadAngleOffset;
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

    public static WebSocketMessage createAngleCommand(String deviceId, Double angle) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("SET_ANGLE");
        msg.setDeviceId(deviceId);
        msg.setData(angle);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    public static WebSocketMessage createSafeAngleCommand(String deviceId,
                                                          Double currentAngle,
                                                          Double targetAngle,
                                                          Double shortestDiff,
                                                          Integer rotationDirection,
                                                          Double rotationDegrees) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("SAFE_SET_ANGLE");
        msg.setDeviceId(deviceId);
        msg.setTargetAngle(targetAngle);
        msg.setCurrentAngle(currentAngle);
        msg.setShortestDiff(shortestDiff);
        msg.setRotationDirection(rotationDirection);
        msg.setRotationDegrees(rotationDegrees);
        msg.setData(targetAngle);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    public static WebSocketMessage createStatusReport(String deviceId, Double currentAngle) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("STATUS_REPORT");
        msg.setDeviceId(deviceId);
        msg.setData(currentAngle);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    public static WebSocketMessage createWindSpeedReport(String deviceId, Double windSpeed, Double windDirection) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("WIND_SPEED_REPORT");
        msg.setDeviceId(deviceId);
        msg.setData(windSpeed);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setWindSpeed(windSpeed);
        msg.setWindDirection(windDirection);
        return msg;
    }

    public static WebSocketMessage createProtectionControl(String deviceId,
                                                         Boolean protectionEnabled,
                                                         Boolean autoUnloadEnabled,
                                                         Double unloadAngleOffset,
                                                         Double windSpeedThreshold) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("PROTECTION_CONTROL");
        msg.setDeviceId(deviceId);
        msg.setProtectionEnabled(protectionEnabled);
        msg.setAutoUnloadEnabled(autoUnloadEnabled);
        msg.setUnloadAngleOffset(unloadAngleOffset);
        msg.setWindSpeedThreshold(windSpeedThreshold);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    public static WebSocketMessage createProtectionStatus(String deviceId,
                                                         Boolean protectionEnabled,
                                                         Boolean protectionActive,
                                                         Boolean autoUnloadEnabled,
                                                         Double currentWindSpeed,
                                                         Double windSpeedThreshold,
                                                         Double unloadTargetAngle,
                                                         Double currentNacelleAngle,
                                                         String reason) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("PROTECTION_STATUS");
        msg.setDeviceId(deviceId);
        msg.setProtectionEnabled(protectionEnabled);
        msg.setProtectionActive(protectionActive);
        msg.setAutoUnloadEnabled(autoUnloadEnabled);
        msg.setWindSpeed(currentWindSpeed);
        msg.setWindSpeedThreshold(windSpeedThreshold);
        msg.setUnloadTargetAngle(unloadTargetAngle);
        msg.setCurrentNacelleAngle(currentNacelleAngle);
        msg.setReason(reason);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }
}
