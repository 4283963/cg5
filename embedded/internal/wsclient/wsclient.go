package wsclient

import (
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/url"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"windturbine-embedded/internal/anemometer"
	"windturbine-embedded/internal/config"
	"windturbine-embedded/internal/motor"
)

type TargetAngleMessage struct {
	Type              string  `json:"type"`
	DeviceID          string  `json:"deviceId"`
	Angle             float64 `json:"angle"`
	Data              float64 `json:"data"`
	TargetAngle       float64 `json:"targetAngle"`
	CurrentAngle      float64 `json:"currentAngle"`
	ShortestDiff      float64 `json:"shortestDiff"`
	RotationDirection int     `json:"rotationDirection"`
	RotationDegrees   float64 `json:"rotationDegrees"`
	Reason            string  `json:"reason"`
}

type CurrentAngleMessage struct {
	Type     string           `json:"type"`
	DeviceID string           `json:"deviceId"`
	Angle    float64          `json:"angle"`
	Data     float64          `json:"data"`
	Status   motor.MotorStatus `json:"status"`
}

type WindSpeedMessage struct {
	Type        string  `json:"type"`
	DeviceID    string  `json:"deviceId"`
	WindSpeed   float64 `json:"windSpeed"`
	WindDirection float64 `json:"windDirection"`
	Data        float64 `json:"data"`
	Timestamp   int64   `json:"timestamp"`
}

type ProtectionControlMessage struct {
	Type              string `json:"type"`
	DeviceID          string `json:"deviceId"`
	ProtectionEnabled bool   `json:"protectionEnabled"`
	AutoUnloadEnabled bool   `json:"autoUnloadEnabled"`
	UnloadAngleOffset float64 `json:"unloadAngleOffset"`
	WindSpeedThreshold float64 `json:"windSpeedThreshold"`
}

type ProtectionStatusMessage struct {
	Type                string  `json:"type"`
	DeviceID            string  `json:"deviceId"`
	ProtectionEnabled   bool    `json:"protectionEnabled"`
	ProtectionActive    bool    `json:"protectionActive"`
	AutoUnloadEnabled   bool    `json:"autoUnloadEnabled"`
	CurrentWindSpeed    float64 `json:"currentWindSpeed"`
	WindSpeedThreshold  float64 `json:"windSpeedThreshold"`
	UnloadTargetAngle   float64 `json:"unloadTargetAngle"`
	CurrentNacelleAngle float64 `json:"currentNacelleAngle"`
	Reason              string  `json:"reason"`
}

type HeartbeatMessage struct {
	Type      string `json:"type"`
	DeviceID  string `json:"deviceId"`
	Timestamp int64  `json:"timestamp"`
}

type WSClient struct {
	cfg             *config.Config
	motorCtrl       *motor.MotorController
	windSensor      anemometer.Anemometer
	conn            *websocket.Conn
	mu              sync.Mutex
	stopChan        chan struct{}
	reconnectChan   chan struct{}
	connected       bool
	running         bool
	reconnectDelay  time.Duration

	protectionEnabled    bool
	autoUnloadEnabled    bool
	protectionActive     bool
	unloadAngleOffset    float64
	windSpeedThreshold   float64
	lastUnloadAngle      float64
}

func NewWSClient(cfg *config.Config, motorCtrl *motor.MotorController, windSensor anemometer.Anemometer) *WSClient {
	return &WSClient{
		cfg:                cfg,
		motorCtrl:          motorCtrl,
		windSensor:         windSensor,
		stopChan:           make(chan struct{}),
		reconnectChan:      make(chan struct{}, 1),
		reconnectDelay:     3 * time.Second,
		protectionEnabled:  true,
		autoUnloadEnabled:  true,
		unloadAngleOffset:  30.0,
		windSpeedThreshold: 25.0,
	}
}

func (w *WSClient) Start() {
	w.mu.Lock()
	if w.running {
		w.mu.Unlock()
		return
	}
	w.running = true
	w.mu.Unlock()

	log.Println("[WebSocket] 客户端启动")
	go w.run()
}

func (w *WSClient) Stop() {
	w.mu.Lock()
	if !w.running {
		w.mu.Unlock()
		return
	}
	w.running = false
	close(w.stopChan)
	w.disconnect()
	w.mu.Unlock()

	log.Println("[WebSocket] 客户端已停止")
}

func (w *WSClient) run() {
	go w.reconnectLoop()

	angleTicker := time.NewTicker(time.Duration(w.cfg.Report.CurrentAngleIntervalMs) * time.Millisecond)
	defer angleTicker.Stop()

	heartbeatTicker := time.NewTicker(time.Duration(w.cfg.Report.HeartbeatIntervalSec) * time.Second)
	defer heartbeatTicker.Stop()

	windTicker := time.NewTicker(time.Duration(w.cfg.Report.WindSpeedIntervalMs) * time.Millisecond)
	defer windTicker.Stop()

	protectionTicker := time.NewTicker(500 * time.Millisecond)
	defer protectionTicker.Stop()

	for {
		select {
		case <-w.stopChan:
			return
		case <-angleTicker.C:
			w.sendCurrentAngle()
		case <-heartbeatTicker.C:
			w.sendHeartbeat()
		case <-windTicker.C:
			w.sendWindSpeed()
		case <-protectionTicker.C:
			w.checkWindProtection()
		}
	}
}

func (w *WSClient) checkWindProtection() {
	w.mu.Lock()
	defer w.mu.Unlock()

	if !w.protectionEnabled || !w.autoUnloadEnabled {
		return
	}

	windSpeed := w.windSensor.GetWindSpeed()
	windDirection := w.windSensor.GetWindDirection()
	wasActive := w.protectionActive

	if windSpeed >= w.windSpeedThreshold {
		if !w.protectionActive {
			w.protectionActive = true
			log.Printf("========================================")
			log.Printf("🌪️  强风保护已激活！")
			log.Printf("  当前风速:    %.1f m/s (阈值: %.1f m/s)", windSpeed, w.windSpeedThreshold)
			log.Printf("  当前风向:    %.1f°", windDirection)
			log.Printf("  卸载偏移角:  +%.1f°", w.unloadAngleOffset)
			log.Printf("========================================")
		}

		unloadAngle := normalizeAngle(windDirection + w.unloadAngleOffset)
		currentAngle := w.motorCtrl.GetCurrentAngle()

		if math.Abs(shortestAngleDiff(currentAngle, unloadAngle)) > 1.0 {
			log.Printf("[强风保护] 自动调整偏航角: 风向%.1f° → 卸载角%.1f° (偏移+%.1f°)",
				windDirection, unloadAngle, w.unloadAngleOffset)
			w.motorCtrl.SetTargetAngle(unloadAngle)
			w.lastUnloadAngle = unloadAngle
		}

		if !wasActive || w.protectionActive {
			go w.sendProtectionStatus("强风保护中，自动卸载已启动")
		}
	} else {
		if w.protectionActive {
			w.protectionActive = false
			log.Printf("========================================")
			log.Printf("✅ 强风保护已解除")
			log.Printf("  当前风速:    %.1f m/s (阈值: %.1f m/s)", windSpeed, w.windSpeedThreshold)
			log.Printf("========================================")
			go w.sendProtectionStatus("风速恢复正常，保护已解除")
		}
	}
}

func (w *WSClient) reconnectLoop() {
	for {
		select {
		case <-w.stopChan:
			return
		default:
		}

		if err := w.connect(); err != nil {
			log.Printf("[WebSocket] 连接失败: %v, %v秒后重试", err, w.reconnectDelay.Seconds())
			time.Sleep(w.reconnectDelay)
			continue
		}

		w.readLoop()

		log.Printf("[WebSocket] 连接断开, %v秒后重连", w.reconnectDelay.Seconds())
		time.Sleep(w.reconnectDelay)
	}
}

func (w *WSClient) connect() error {
	wsURL := fmt.Sprintf("%s/%s", w.cfg.Backend.WSUrl, w.cfg.DeviceID)
	u, err := url.Parse(wsURL)
	if err != nil {
		return fmt.Errorf("解析URL失败: %w", err)
	}

	log.Printf("[WebSocket] 正在连接: %s", u.String())

	conn, _, err := websocket.DefaultDialer.Dial(u.String(), nil)
	if err != nil {
		return fmt.Errorf("拨号失败: %w", err)
	}

	w.mu.Lock()
	w.conn = conn
	w.connected = true
	w.mu.Unlock()

	log.Printf("[WebSocket] 连接成功: %s", u.String())
	return nil
}

func (w *WSClient) disconnect() {
	w.mu.Lock()
	defer w.mu.Unlock()

	if w.conn != nil {
		w.conn.Close()
		w.conn = nil
	}
	w.connected = false
}

func (w *WSClient) readLoop() {
	for {
		w.mu.Lock()
		conn := w.conn
		w.mu.Unlock()

		if conn == nil {
			return
		}

		_, msg, err := conn.ReadMessage()
		if err != nil {
			log.Printf("[WebSocket] 读取消息失败: %v", err)
			w.disconnect()
			return
		}

		w.handleMessage(msg)
	}
}

func (w *WSClient) handleMessage(msg []byte) {
	var base struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(msg, &base); err != nil {
		log.Printf("[WebSocket] 解析消息失败: %v", err)
		return
	}

	switch base.Type {
	case "target_angle", "SET_ANGLE":
		w.handleSetAngle(msg, false)

	case "SAFE_SET_ANGLE":
		w.handleSetAngle(msg, true)

	case "PROTECTION_CONTROL":
		w.handleProtectionControl(msg)

	case "PROTECTION_STATUS_QUERY":
		go w.sendProtectionStatus("状态查询响应")

	case "SET_WIND_SPEED":
		var cmd struct {
			WindSpeed float64 `json:"windSpeed"`
		}
		if err := json.Unmarshal(msg, &cmd); err == nil {
			w.windSensor.SetWindSpeed(cmd.WindSpeed)
		}

	case "SIMULATE_STORM":
		var cmd struct {
			Enable bool `json:"enable"`
		}
		if err := json.Unmarshal(msg, &cmd); err == nil {
			if sim, ok := w.windSensor.(*anemometer.MockAnemometer); ok {
				sim.SimulateStorm(cmd.Enable)
			}
		}

	default:
		log.Printf("[WebSocket] 收到未知消息类型: %s", base.Type)
	}
}

func (w *WSClient) handleSetAngle(msg []byte, isSafe bool) {
	var target TargetAngleMessage
	if err := json.Unmarshal(msg, &target); err != nil {
		log.Printf("[WebSocket] 解析角度指令失败: %v", err)
		return
	}

	w.mu.Lock()
	protectionActive := w.protectionActive
	w.mu.Unlock()

	if protectionActive {
		log.Printf("[强风保护] 手动控制已锁定，忽略角度指令: %.2f°", target.TargetAngle)
		log.Printf("[强风保护] 原因: %s", target.Reason)
		return
	}

	if isSafe {
		log.Printf("========================================")
		log.Printf("🔒 收到安全角度指令 (SAFE_SET_ANGLE)")
		log.Printf("  设备ID:         %s", target.DeviceID)
		log.Printf("  当前角度:       %.2f°", target.CurrentAngle)
		log.Printf("  目标角度:       %.2f°", target.TargetAngle)
		log.Printf("  最短路径差值:   %.2f°", target.ShortestDiff)
		log.Printf("  旋转方向:       %s", getDirectionString(target.RotationDirection))
		log.Printf("  预计旋转度数:   %.2f°", target.RotationDegrees)
		log.Printf("========================================")

		localCurrentAngle := w.motorCtrl.GetCurrentAngle()
		naiveDiff := target.TargetAngle - localCurrentAngle
		if math.Abs(naiveDiff) > math.Abs(target.ShortestDiff)+1.0 {
			log.Printf("⚠️  安全校验: 本地简单差值 %.2f° 与后端最短路径差值 %.2f° 不符，已采用后端安全路径！",
				naiveDiff, target.ShortestDiff)
		}

		if math.Abs(target.ShortestDiff) > 180.0 {
			log.Printf("❌ 安全拦截: 旋转角度 %.2f° 超过最大安全限制 180°，指令已拒绝！",
				math.Abs(target.ShortestDiff))
			return
		}
	}

	finalAngle := target.TargetAngle
	if finalAngle == 0 && target.Data != 0 {
		finalAngle = target.Data
	}
	if finalAngle == 0 && target.Angle != 0 {
		finalAngle = target.Angle
	}

	log.Printf("[WebSocket] 执行目标角度指令: %.2f°", finalAngle)
	w.motorCtrl.SetTargetAngle(finalAngle)
}

func (w *WSClient) handleProtectionControl(msg []byte) {
	var cmd ProtectionControlMessage
	if err := json.Unmarshal(msg, &cmd); err != nil {
		log.Printf("[WebSocket] 解析保护控制指令失败: %v", err)
		return
	}

	w.mu.Lock()
	defer w.mu.Unlock()

	w.protectionEnabled = cmd.ProtectionEnabled
	w.autoUnloadEnabled = cmd.AutoUnloadEnabled
	if cmd.UnloadAngleOffset > 0 {
		w.unloadAngleOffset = cmd.UnloadAngleOffset
	}
	if cmd.WindSpeedThreshold > 0 {
		w.windSpeedThreshold = cmd.WindSpeedThreshold
	}

	log.Printf("========================================")
	log.Printf("⚙️  保护控制参数已更新")
	log.Printf("  保护功能:       %s", boolToString(w.protectionEnabled))
	log.Printf("  自动卸载:       %s", boolToString(w.autoUnloadEnabled))
	log.Printf("  卸载偏移角:     %.1f°", w.unloadAngleOffset)
	log.Printf("  风速阈值:       %.1f m/s", w.windSpeedThreshold)
	log.Printf("========================================")

	go w.sendProtectionStatus("保护参数已更新")
}

func (w *WSClient) sendCurrentAngle() {
	w.mu.Lock()
	conn := w.conn
	connected := w.connected
	w.mu.Unlock()

	if !connected || conn == nil {
		return
	}

	angle := roundToOneDecimal(w.motorCtrl.GetCurrentAngle())
	status := w.motorCtrl.GetStatus()

	msg := CurrentAngleMessage{
		Type:     "STATUS_REPORT",
		DeviceID: w.cfg.DeviceID,
		Angle:    angle,
		Data:     angle,
		Status:   status,
	}

	data, err := json.Marshal(msg)
	if err != nil {
		log.Printf("[WebSocket] 序列化当前角度消息失败: %v", err)
		return
	}

	if err := conn.WriteMessage(websocket.TextMessage, data); err != nil {
		log.Printf("[WebSocket] 发送当前角度消息失败: %v", err)
		w.disconnect()
		return
	}

	log.Printf("[WebSocket] 上报当前角度: %.1f°, 状态: %s", angle, status)
}

func (w *WSClient) sendWindSpeed() {
	w.mu.Lock()
	conn := w.conn
	connected := w.connected
	w.mu.Unlock()

	if !connected || conn == nil {
		return
	}

	windSpeed := w.windSensor.GetWindSpeed()
	windDirection := w.windSensor.GetWindDirection()

	msg := WindSpeedMessage{
		Type:          "WIND_SPEED_REPORT",
		DeviceID:      w.cfg.DeviceID,
		WindSpeed:     windSpeed,
		WindDirection: windDirection,
		Data:          windSpeed,
		Timestamp:     time.Now().Unix(),
	}

	data, err := json.Marshal(msg)
	if err != nil {
		log.Printf("[WebSocket] 序列化风速消息失败: %v", err)
		return
	}

	if err := conn.WriteMessage(websocket.TextMessage, data); err != nil {
		log.Printf("[WebSocket] 发送风速消息失败: %v", err)
		w.disconnect()
		return
	}

	log.Printf("[WebSocket] 上报风速: %.1f m/s, 风向: %.1f°", windSpeed, windDirection)
}

func (w *WSClient) sendProtectionStatus(reason string) {
	w.mu.Lock()
	conn := w.conn
	connected := w.connected
	protectionEnabled := w.protectionEnabled
	protectionActive := w.protectionActive
	autoUnloadEnabled := w.autoUnloadEnabled
	windSpeedThreshold := w.windSpeedThreshold
	lastUnloadAngle := w.lastUnloadAngle
	w.mu.Unlock()

	if !connected || conn == nil {
		return
	}

	msg := ProtectionStatusMessage{
		Type:                "PROTECTION_STATUS",
		DeviceID:            w.cfg.DeviceID,
		ProtectionEnabled:   protectionEnabled,
		ProtectionActive:    protectionActive,
		AutoUnloadEnabled:   autoUnloadEnabled,
		CurrentWindSpeed:    w.windSensor.GetWindSpeed(),
		WindSpeedThreshold:  windSpeedThreshold,
		UnloadTargetAngle:   lastUnloadAngle,
		CurrentNacelleAngle: w.motorCtrl.GetCurrentAngle(),
		Reason:              reason,
	}

	data, err := json.Marshal(msg)
	if err != nil {
		log.Printf("[WebSocket] 序列化保护状态消息失败: %v", err)
		return
	}

	if err := conn.WriteMessage(websocket.TextMessage, data); err != nil {
		log.Printf("[WebSocket] 发送保护状态消息失败: %v", err)
		w.disconnect()
		return
	}

	log.Printf("[WebSocket] 上报保护状态: 保护=%v, 激活=%v, 风速=%.1f m/s",
		protectionEnabled, protectionActive, w.windSensor.GetWindSpeed())
}

func (w *WSClient) sendHeartbeat() {
	w.mu.Lock()
	conn := w.conn
	connected := w.connected
	w.mu.Unlock()

	if !connected || conn == nil {
		return
	}

	msg := HeartbeatMessage{
		Type:      "heartbeat",
		DeviceID:  w.cfg.DeviceID,
		Timestamp: time.Now().Unix(),
	}

	data, err := json.Marshal(msg)
	if err != nil {
		log.Printf("[WebSocket] 序列化心跳消息失败: %v", err)
		return
	}

	if err := conn.WriteMessage(websocket.TextMessage, data); err != nil {
		log.Printf("[WebSocket] 发送心跳消息失败: %v", err)
		w.disconnect()
		return
	}

	log.Printf("[WebSocket] 发送心跳: deviceId=%s, timestamp=%d", msg.DeviceID, msg.Timestamp)
}

func roundToOneDecimal(v float64) float64 {
	return float64(int(v*10+0.5)) / 10
}

func getDirectionString(direction int) string {
	switch direction {
	case 1:
		return "顺时针"
	case -1:
		return "逆时针"
	default:
		return "无"
	}
}

func boolToString(b bool) string {
	if b {
		return "启用"
	}
	return "禁用"
}

func normalizeAngle(angle float64) float64 {
	for angle < 0 {
		angle += 360
	}
	for angle >= 360 {
		angle -= 360
	}
	return angle
}

func shortestAngleDiff(from, to float64) float64 {
	diff := to - from
	for diff > 180 {
		diff -= 360
	}
	for diff < -180 {
		diff += 360
	}
	return diff
}
