package com.cg5.backend.controller;

import com.cg5.backend.dto.AngleRequest;
import com.cg5.backend.dto.DeviceInfo;
import com.cg5.backend.dto.DeviceStatus;
import com.cg5.backend.dto.Result;
import com.cg5.backend.manager.DeviceSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class YawController {

    private static final Logger log = LoggerFactory.getLogger(YawController.class);

    private final DeviceSessionManager sessionManager;

    public YawController(DeviceSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @PutMapping("/yaw/{deviceId}/angle")
    public Result<Void> setTargetAngle(@PathVariable String deviceId,
                                       @RequestBody AngleRequest request) {
        if (request == null || request.getAngle() == null) {
            return Result.error(400, "angle is required");
        }
        Double angle = request.getAngle();
        if (angle < -180 || angle > 180) {
            return Result.error(400, "angle must be between -180 and 180");
        }
        boolean success = sessionManager.sendAngleToDevice(deviceId, angle);
        if (!success) {
            return Result.error(404, "Device " + deviceId + " is not online");
        }
        log.info("Set target angle for device {}: {}", deviceId, angle);
        return Result.success(null);
    }

    @GetMapping("/yaw/{deviceId}/status")
    public Result<DeviceStatus> getDeviceStatus(@PathVariable String deviceId) {
        DeviceStatus status = sessionManager.getDeviceStatus(deviceId);
        return Result.success(status);
    }

    @GetMapping("/devices")
    public Result<List<DeviceInfo>> getAllOnlineDevices() {
        List<DeviceInfo> devices = sessionManager.getAllOnlineDevices();
        return Result.success(devices);
    }
}
