package com.cg5.backend.manager;

import com.alibaba.fastjson2.JSON;
import com.cg5.backend.dto.DeviceInfo;
import com.cg5.backend.dto.DeviceStatus;
import com.cg5.backend.dto.WebSocketMessage;
import com.cg5.backend.util.AngleUtils;
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

    private static final double MAX_SAFE_ROTATION_DEGREES = 180.0;
    private static final double DEFAULT_CURRENT_ANGLE = 0.0;

    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> deviceConnectTimes = new ConcurrentHashMap<>();
    private final Map<String, Double> deviceTargetAngles = new ConcurrentHashMap<>();
    private final Map<String, Double> deviceCurrentAngles = new ConcurrentHashMap<>();
    private final Map<String, Long> deviceLastUpdateTimes = new ConcurrentHashMap<>();

    private final Map<String, WebSocketSession> adminSessions = new ConcurrentHashMap<>();

    public void addDeviceSession(String deviceId, WebSocketSession session) {
        deviceSessions.put(deviceId, session);
        deviceConnectTimes.put(deviceId, System.currentTimeMillis());
        log.info("设备连接成功: {}, 当前在线设备数: {}", deviceId, deviceSessions.size());
        broadcastToAdmins(WebSocketMessage.createStatusReport(deviceId, getCurrentAngle(deviceId)));
    }

    public void removeDeviceSession(String deviceId) {
        deviceSessions.remove(deviceId);
        deviceConnectTimes.remove(deviceId);
        log.info("设备断开连接: {}, 当前在线设备数: {}", deviceId, deviceSessions.size());
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
        log.info("管理端连接成功: {}, 当前在线管理端数: {}", sessionId, adminSessions.size());
    }

    public void removeAdminSession(String sessionId) {
        adminSessions.remove(sessionId);
        log.info("管理端断开连接: {}, 当前在线管理端数: {}", sessionId, adminSessions.size());
    }

    public boolean sendAngleToDevice(String deviceId, Double targetAngle) {
        return sendSafeAngleToDevice(deviceId, targetAngle, true);
    }

    public boolean sendSafeAngleToDevice(String deviceId, Double rawTargetAngle, boolean useShortestPath) {
        WebSocketSession session = deviceSessions.get(deviceId);
        if (session == null || !session.isOpen()) {
            log.warn("设备 {} 离线，无法发送角度指令", deviceId);
            return false;
        }

        Double currentAngle = getCurrentAngle(deviceId);
        if (currentAngle == null) {
            currentAngle = DEFAULT_CURRENT_ANGLE;
            log.warn("设备 {} 当前角度未知，使用默认值 {}°", deviceId, currentAngle);
        }

        double normalizedCurrent = AngleUtils.normalizeTo0To360(currentAngle);
        double normalizedTarget = AngleUtils.normalizeTo0To360(rawTargetAngle);

        double shortestDiff = AngleUtils.calculateShortestDiff(normalizedCurrent, normalizedTarget);
        int rotationDirection = AngleUtils.getRotationDirection(shortestDiff);
        double rotationDegrees = AngleUtils.getAbsoluteRotationDegrees(shortestDiff);

        if (!useShortestPath) {
            log.warn("⚠️  已禁用最短路径算法，这可能导致不安全的旋转！");
        }

        if (rotationDegrees > MAX_SAFE_ROTATION_DEGREES) {
            log.error("❌ 安全拦截: 旋转角度 {}° 超过最大安全限制 {}°，指令已拒绝！",
                    rotationDegrees, MAX_SAFE_ROTATION_DEGREES);
            return false;
        }

        double optimizedTarget = AngleUtils.calculateOptimizedTargetAngle(normalizedCurrent, normalizedTarget);

        if (Math.abs(shortestDiff) < 0.5) {
            log.info("设备 {} 当前角度 {}° 与目标角度 {}° 已对齐，无需旋转",
                    deviceId,
                    AngleUtils.formatAngle(normalizedCurrent),
                    AngleUtils.formatAngle(normalizedTarget));
            deviceTargetAngles.put(deviceId, optimizedTarget);
            return true;
        }

        String directionStr = rotationDirection == AngleUtils.CLOCKWISE ? "顺时针" : "逆时针";
        log.info("🚀 安全角度指令计算完成: 设备={}, 当前={}°, 目标={}°, 最短路径={}°{}, 旋转度数={}°",
                deviceId,
                AngleUtils.formatAngle(normalizedCurrent),
                AngleUtils.formatAngle(normalizedTarget),
                directionStr,
                AngleUtils.formatAngle(Math.abs(shortestDiff)),
                AngleUtils.formatAngle(rotationDegrees));

        try {
            WebSocketMessage message = WebSocketMessage.createSafeAngleCommand(
                    deviceId,
                    normalizedCurrent,
                    optimizedTarget,
                    shortestDiff,
                    rotationDirection,
                    rotationDegrees
            );

            session.sendMessage(new TextMessage(JSON.toJSONString(message)));
            deviceTargetAngles.put(deviceId, optimizedTarget);

            log.info("✅ 安全角度指令已发送至设备 {}: 目标={}°, 方向={}, 预计旋转={}°",
                    deviceId,
                    AngleUtils.formatAngle(optimizedTarget),
                    directionStr,
                    AngleUtils.formatAngle(rotationDegrees));

            return true;
        } catch (IOException e) {
            log.error("❌ 发送角度指令至设备 {} 失败", deviceId, e);
            return false;
        }
    }

    @Deprecated
    public boolean sendRawAngleToDevice(String deviceId, Double angle) {
        WebSocketSession session = deviceSessions.get(deviceId);
        if (session == null || !session.isOpen()) {
            log.warn("设备 {} 离线，无法发送角度指令", deviceId);
            return false;
        }
        try {
            WebSocketMessage message = WebSocketMessage.createAngleCommand(deviceId, angle);
            session.sendMessage(new TextMessage(JSON.toJSONString(message)));
            deviceTargetAngles.put(deviceId, angle);
            log.warn("⚠️  已发送原始角度指令（无最短路径保护）至设备 {}: {}", deviceId, angle);
            return true;
        } catch (IOException e) {
            log.error("发送原始角度指令至设备 {} 失败", deviceId, e);
            return false;
        }
    }

    public void updateDeviceCurrentAngle(String deviceId, Double angle) {
        double normalizedAngle = AngleUtils.normalizeTo0To360(angle);
        deviceCurrentAngles.put(deviceId, normalizedAngle);
        deviceLastUpdateTimes.put(deviceId, System.currentTimeMillis());
        log.debug("设备 {} 当前角度已更新: {}° (原始: {}°)", deviceId,
                AngleUtils.formatAngle(normalizedAngle),
                AngleUtils.formatAngle(angle));
        broadcastToAdmins(WebSocketMessage.createStatusReport(deviceId, normalizedAngle));
    }

    public Double getCurrentAngle(String deviceId) {
        Double angle = deviceCurrentAngles.get(deviceId);
        if (angle != null) {
            return AngleUtils.normalizeTo0To360(angle);
        }
        return angle;
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

        Double current = getCurrentAngle(deviceId);
        Double target = getTargetAngle(deviceId);
        if (current != null && target != null) {
            double diff = AngleUtils.calculateShortestDiff(current, target);
            status.setShortestDiff(diff);
            status.setRotationDirection(AngleUtils.getRotationDirection(diff));
            status.setRotationDegrees(AngleUtils.getAbsoluteRotationDegrees(diff));
        }

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
                info.setCurrentAngle(getCurrentAngle(entry.getKey()));
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
                    log.error("向管理端 {} 广播消息失败", entry.getKey(), e);
                }
            }
        }
    }
}
