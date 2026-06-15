package com.cg5.backend.config;

import com.cg5.backend.websocket.AdminWebSocketHandler;
import com.cg5.backend.websocket.DeviceWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DeviceWebSocketHandler deviceWebSocketHandler;
    private final AdminWebSocketHandler adminWebSocketHandler;

    public WebSocketConfig(DeviceWebSocketHandler deviceWebSocketHandler,
                           AdminWebSocketHandler adminWebSocketHandler) {
        this.deviceWebSocketHandler = deviceWebSocketHandler;
        this.adminWebSocketHandler = adminWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(deviceWebSocketHandler, "/ws/device/{deviceId}")
                .setAllowedOrigins("*");
        registry.addHandler(adminWebSocketHandler, "/ws/admin")
                .setAllowedOrigins("*");
    }
}
