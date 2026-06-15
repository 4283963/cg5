package com.cg5.backend.manager;

import com.alibaba.fastjson2.JSON;
import com.cg5.backend.dto.DeviceInfo;
import com.cg5.backend.dto.DeviceStatus;
import com.cg5.backend.dto.WebSocketMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceSessionManager {

    private static final Logger log = LoggerFactory.getLogger(DeviceSessionManager.class);

    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> deviceConnectTimes = new ConcurrentHashMap<>();
    private final Map<String, Double> deviceTargetAngles = new ConcurrentHashMap<>();
    private final Map<String, Double> deviceCurrentAngles = new ConcurrentHashMap<>();
    private final Map<String, Long> deviceLastUpdateTimes = new ConcurrentHashMap<>();

    private final Map<String, WebSocketSession> adminSessions = new ConcurrentHashMap<>();

    public void addDeviceSession(String deviceId, WebSocketSession session) {
        deviceSessions.put(deviceId, session);
        deviceConnectTimes.put(deviceId, System.currentTimeMillis());
        log.info("Device connected: {}, total devices: {}", deviceId, deviceSessions.size());
        broadcastToAdmins(WebSocketMessage.createStatusReport(deviceId, getCurrentAngle(deviceId)));
    }

    public void removeDeviceSession(String deviceId) {
        deviceSessions.remove(deviceId);
        deviceConnectTimes.remove(deviceId);
        log.info("Device disconnected: {}, total devices: {}", deviceId, deviceSessions.size());
    }

    public WebSocketSession getDeviceSession(String deviceId) {
        return deviceSessions.get(deviceId);
    }

    public boolean isDeviceOnline(String deviceId) {
        WebSocketSession session = deviceSessions.get(deviceId);
        return session != null && session.isOpen();
    }

    public void addAdminSession(String sessionId, WebSocketSession session) {
        adminSessions.put(sessionId, session);
        log.info("Admin connected: {}, total admins: {}", sessionId, adminSessions.size());
    }

    public void removeAdminSession(String sessionId) {
        adminSessions.remove(sessionId);
        log.info("Admin disconnected: {}, total admins: {}", sessionId, adminSessions.size());
    }

    public boolean sendAngleToDevice(String deviceId, Double angle) {
        WebSocketSession session = deviceSessions.get(deviceId);
        if (session == null || !session.isOpen()) {
            log.warn("Device {} is offline, cannot send angle command", deviceId);
            return false;
        }
        try {
            WebSocketMessage message = WebSocketMessage.createAngleCommand(deviceId, angle);
            session.sendMessage(new TextMessage(JSON.toJSONString(message)));
            deviceTargetAngles.put(deviceId, angle);
            log.info("Sent angle command to device {}: {}", deviceId, angle);
            return true;
        } catch (IOException e) {
            log.error("Failed to send angle command to device {}", deviceId, e);
            return false;
        }
    }

    public void updateDeviceCurrentAngle(String deviceId, Double angle) {
        deviceCurrentAngles.put(deviceId, angle);
        deviceLastUpdateTimes.put(deviceId, System.currentTimeMillis());
        log.debug("Device {} current angle updated: {}", deviceId, angle);
        broadcastToAdmins(WebSocketMessage.createStatusReport(deviceId, angle));
    }

    public Double getCurrentAngle(String deviceId) {
        return deviceCurrentAngles.get(deviceId);
    }

    public Double getTargetAngle(String deviceId) {
        return deviceTargetAngles.get(deviceId);
    }

    public DeviceStatus getDeviceStatus(String deviceId) {
        DeviceStatus status = new DeviceStatus();
        status.setDeviceId(deviceId);
        status.setCurrentAngle(getCurrentAngle(deviceId));
        status.setTargetAngle(getTargetAngle(deviceId));
        status.setOnline(isDeviceOnline(deviceId));
        status.setLastUpdateTime(deviceLastUpdateTimes.get(deviceId));
        return status;
    }

    public List<DeviceInfo> getAllOnlineDevices() {
        List<DeviceInfo> devices = new ArrayList<>();
        for (Map.Entry<String, WebSocketSession> entry : deviceSessions.entrySet()) {
            if (entry.getValue().isOpen()) {
                DeviceInfo info = new DeviceInfo();
                info.setDeviceId(entry.getKey());
                info.setOnline(true);
                info.setConnectTime(deviceConnectTimes.get(entry.getKey()));
                devices.add(info);
            }
        }
        return devices;
    }

    public void broadcastToAdmins(WebSocketMessage message) {
        String jsonMessage = JSON.toJSONString(message);
        for (Map.Entry<String, WebSocketSession> entry : adminSessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(jsonMessage));
                } catch (IOException e) {
                    log.error("Failed to broadcast message to admin {}", entry.getKey(), e);
                }
            }
        }
    }
}
