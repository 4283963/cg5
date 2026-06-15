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
}
