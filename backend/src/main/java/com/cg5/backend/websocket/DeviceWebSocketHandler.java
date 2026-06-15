package com.cg5.backend.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cg5.backend.manager.DeviceSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.net.URI;
import java.util.Map;

@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceWebSocketHandler.class);
    private static final String DEVICE_ID_PATH = "/ws/device/{deviceId}";

    private final DeviceSessionManager sessionManager;

    public DeviceWebSocketHandler(DeviceSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = extractDeviceId(session);
        if (deviceId == null || deviceId.isEmpty()) {
            session.close(CloseStatus.BAD_DATA.withReason("deviceId is required"));
            return;
        }
        sessionManager.addDeviceSession(deviceId, session);
        session.getAttributes().put("deviceId", deviceId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String deviceId = (String) session.getAttributes().get("deviceId");
        if (deviceId == null) {
            return;
        }
        try {
            JSONObject json = JSON.parseObject(message.getPayload());
            String type = json.getString("type");
            if ("STATUS_REPORT".equals(type)) {
                Double angle = json.getDouble("data");
                if (angle != null) {
                    sessionManager.updateDeviceCurrentAngle(deviceId, angle);
                }
            } else if ("ANGLE_REPORT".equals(type)) {
                Double angle = json.getDouble("angle");
                if (angle == null) {
                    angle = json.getDouble("data");
                }
                if (angle != null) {
                    sessionManager.updateDeviceCurrentAngle(deviceId, angle);
                }
            } else {
                log.debug("Received message from device {}: {}", deviceId, message.getPayload());
            }
        } catch (Exception e) {
            log.error("Failed to parse message from device {}", deviceId, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String deviceId = (String) session.getAttributes().get("deviceId");
        if (deviceId != null) {
            sessionManager.removeDeviceSession(deviceId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String deviceId = (String) session.getAttributes().get("deviceId");
        log.error("Transport error for device {}", deviceId, exception);
    }

    private String extractDeviceId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        UriTemplate template = new UriTemplate(DEVICE_ID_PATH);
        Map<String, String> variables = template.match(uri.getPath());
        return variables.get("deviceId");
    }
}
