package com.cg5.backend.controller;

import com.cg5.backend.dto.*;
import com.cg5.backend.manager.DeviceSessionManager;
import com.cg5.backend.util.AngleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class YawController {

    private static final Logger log = LoggerFactory.getLogger(YawController.class);

    private static final double MIN_ANGLE = -180.0;
    private static final double MAX_ANGLE = 180.0;

    private final DeviceSessionManager sessionManager;

    public YawController(DeviceSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @PutMapping("/yaw/{deviceId}/angle")
    public Result<Void> setTargetAngle(@PathVariable String deviceId,
                                       @RequestBody AngleRequest request,
                                       @RequestParam(defaultValue = "true") boolean useShortestPath) {
        if (request == null || request.getAngle() == null) {
            log.warn("设备 {} 角度设置请求参数为空", deviceId);
            return Result.error(400, "angle is required");
        }

        Double rawAngle = request.getAngle();

        if (Double.isNaN(rawAngle) || Double.isInfinite(rawAngle)) {
            log.error("设备 {} 收到无效角度值: {}", deviceId, rawAngle);
            return Result.error(400, "invalid angle value");
        }

        if (rawAngle < MIN_ANGLE || rawAngle > MAX_ANGLE) {
            log.error("设备 {} 角度 {}° 超出允许范围 [{}°, {}°]",
                    deviceId, rawAngle, MIN_ANGLE, MAX_ANGLE);
            return Result.error(400,
                    String.format("angle must be between %.0f and %.0f", MIN_ANGLE, MAX_ANGLE));
        }

        if (sessionManager.isProtectionActive(deviceId)) {
            log.warn("🌪️  设备 {} 处于强风保护状态，拒绝手动角度指令: {}°",
                    deviceId, AngleUtils.formatAngle(rawAngle));
            return Result.error(403, "强风保护已激活，手动控制已锁定");
        }

        Double currentAngle = sessionManager.getCurrentAngle(deviceId);
        if (currentAngle != null) {
            double naiveDiff = rawAngle - AngleUtils.normalizeToMinus180To180(currentAngle);
            double shortestDiff = AngleUtils.calculateShortestDiff(currentAngle, rawAngle);

            if (Math.abs(naiveDiff) > Math.abs(shortestDiff) + 1.0) {
                log.warn("⚠️  角度计算安全检查: 设备={}, 当前={}°, 原始目标={}°",
                        deviceId,
                        AngleUtils.formatAngle(currentAngle),
                        AngleUtils.formatAngle(rawAngle));
                log.warn("⚠️  简单差值计算: {}° (危险！可能跨越0度线), 最短路径差值: {}° (安全)",
                        AngleUtils.formatAngle(naiveDiff),
                        AngleUtils.formatAngle(shortestDiff));
                log.warn("⚠️  已自动启用最短路径保护，避免反向绞断集电环！");
            }
        }

        log.info("收到设备 {} 偏航角度设置请求: 原始角度={}°, 最短路径保护={}",
                deviceId, AngleUtils.formatAngle(rawAngle), useShortestPath);

        boolean success = sessionManager.sendSafeAngleToDevice(deviceId, rawAngle, useShortestPath);
        if (!success) {
            if (!sessionManager.isDeviceOnline(deviceId)) {
                log.error("设备 {} 不在线，无法发送角度指令", deviceId);
                return Result.error(404, "Device " + deviceId + " is not online");
            } else if (sessionManager.isProtectionActive(deviceId)) {
                return Result.error(403, "强风保护已激活，手动控制已锁定");
            } else {
                log.error("设备 {} 角度指令发送失败，可能触发安全保护", deviceId);
                return Result.error(500, "Failed to send angle command, possibly blocked by safety protection");
            }
        }

        log.info("✅ 设备 {} 偏航角度指令已成功发送: 目标={}°",
                deviceId, AngleUtils.formatAngle(rawAngle));
        return Result.success(null);
    }

    @GetMapping("/yaw/{deviceId}/status")
    public Result<DeviceStatus> getDeviceStatus(@PathVariable String deviceId) {
        DeviceStatus status = sessionManager.getDeviceStatus(deviceId);

        if (status.getCurrentAngle() != null && status.getTargetAngle() != null) {
            double diff = AngleUtils.calculateShortestDiff(
                    status.getCurrentAngle(),
                    status.getTargetAngle()
            );
            log.debug("设备 {} 状态查询: 当前={}°, 目标={}°, 最短差值={}°",
                    deviceId,
                    AngleUtils.formatAngle(status.getCurrentAngle()),
                    AngleUtils.formatAngle(status.getTargetAngle()),
                    AngleUtils.formatAngle(diff));
        }

        return Result.success(status);
    }

    @GetMapping("/devices")
    public Result<List<DeviceInfo>> getAllOnlineDevices() {
        List<DeviceInfo> devices = sessionManager.getAllOnlineDevices();
        log.debug("查询在线设备列表，共 {} 台设备在线", devices.size());
        return Result.success(devices);
    }

    @PutMapping("/protection/{deviceId}/config")
    public Result<Void> setProtectionConfig(@PathVariable String deviceId,
                                            @RequestBody ProtectionConfig config) {
        if (config == null) {
            return Result.error(400, "config is required");
        }

        log.info("收到设备 {} 保护配置更新请求: 保护={}, 自动卸载={}, 偏移角={}°, 阈值={} m/s",
                deviceId,
                config.getProtectionEnabled(),
                config.getAutoUnloadEnabled(),
                config.getUnloadAngleOffset(),
                config.getWindSpeedThreshold());

        boolean success = sessionManager.setProtectionConfig(
                deviceId,
                config.getProtectionEnabled(),
                config.getAutoUnloadEnabled(),
                config.getUnloadAngleOffset(),
                config.getWindSpeedThreshold()
        );

        if (!success) {
            return Result.error(404, "Device " + deviceId + " is not online");
        }

        return Result.success(null);
    }

    @GetMapping("/protection/{deviceId}/status")
    public Result<ProtectionStatus> getProtectionStatus(@PathVariable String deviceId) {
        ProtectionStatus status = sessionManager.getProtectionStatus(deviceId);

        if (status.getProtectionActive() != null && status.getProtectionActive()) {
            log.warn("🌪️  设备 {} 保护状态查询: 保护已激活, 当前风速={} m/s, 阈值={} m/s",
                    deviceId,
                    status.getCurrentWindSpeed(),
                    status.getWindSpeedThreshold());
        }

        return Result.success(status);
    }

    @GetMapping("/protection/{deviceId}/wind")
    public Result<WindSpeedData> getWindSpeedData(@PathVariable String deviceId) {
        WindSpeedData data = sessionManager.getWindSpeedData(deviceId);
        return Result.success(data);
    }

    @PutMapping("/protection/{deviceId}/enable")
    public Result<Void> enableProtection(@PathVariable String deviceId) {
        log.info("启用设备 {} 强风保护功能", deviceId);
        boolean success = sessionManager.setProtectionConfig(
                deviceId, true, null, null, null);
        if (!success) {
            return Result.error(404, "Device " + deviceId + " is not online");
        }
        return Result.success(null);
    }

    @PutMapping("/protection/{deviceId}/disable")
    public Result<Void> disableProtection(@PathVariable String deviceId) {
        log.warn("⚠️  禁用设备 {} 强风保护功能，请注意安全！", deviceId);
        boolean success = sessionManager.setProtectionConfig(
                deviceId, false, null, null, null);
        if (!success) {
            return Result.error(404, "Device " + deviceId + " is not online");
        }
        return Result.success(null);
    }
}
