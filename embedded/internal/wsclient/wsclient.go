package wsclient

import (
	"encoding/json"
	"fmt"
	"log"
	"net/url"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"windturbine-embedded/internal/config"
	"windturbine-embedded/internal/motor"
)

type TargetAngleMessage struct {
	Type     string  `json:"type"`
	DeviceID string  `json:"deviceId"`
	Angle    float64 `json:"angle"`
	Data     float64 `json:"data"`
}

type CurrentAngleMessage struct {
	Type     string           `json:"type"`
	DeviceID string           `json:"deviceId"`
	Angle    float64          `json:"angle"`
	Data     float64          `json:"data"`
	Status   motor.MotorStatus `json:"status"`
}

type HeartbeatMessage struct {
	Type      string `json:"type"`
	DeviceID  string `json:"deviceId"`
	Timestamp int64  `json:"timestamp"`
}

type WSClient struct {
	cfg             *config.Config
	motorCtrl       *motor.MotorController
	conn            *websocket.Conn
	mu              sync.Mutex
	stopChan        chan struct{}
	reconnectChan   chan struct{}
	connected       bool
	running         bool
	reconnectDelay  time.Duration
}

func NewWSClient(cfg *config.Config, motorCtrl *motor.MotorController) *WSClient {
	return &WSClient{
		cfg:            cfg,
		motorCtrl:      motorCtrl,
		stopChan:       make(chan struct{}),
		reconnectChan:  make(chan struct{}, 1),
		reconnectDelay: 3 * time.Second,
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

	for {
		select {
		case <-w.stopChan:
			return
		case <-angleTicker.C:
			w.sendCurrentAngle()
		case <-heartbeatTicker.C:
			w.sendHeartbeat()
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
		var target TargetAngleMessage
		if err := json.Unmarshal(msg, &target); err != nil {
			log.Printf("[WebSocket] 解析目标角度消息失败: %v", err)
			return
		}
		angle := target.Angle
		if angle == 0 && target.Data != 0 {
			angle = target.Data
		}
		log.Printf("[WebSocket] 收到目标角度指令: %.2f°", angle)
		w.motorCtrl.SetTargetAngle(angle)
	default:
		log.Printf("[WebSocket] 收到未知消息类型: %s, 原始消息: %s", base.Type, string(msg))
	}
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
