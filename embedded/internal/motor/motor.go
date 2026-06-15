package motor

import (
	"log"
	"math"
	"sync"
	"time"

	"windturbine-embedded/internal/compass"
	"windturbine-embedded/internal/config"
	"windturbine-embedded/internal/serial"
)

type MotorStatus string

const (
	StatusRotating MotorStatus = "rotating"
	StatusAligned  MotorStatus = "aligned"
)

type MotorController struct {
	mu              sync.RWMutex
	cfg             *config.MotorConfig
	serialPort      serial.SerialPort
	compass         compass.Compass
	currentAngle    float64
	targetAngle     float64
	status          MotorStatus
	stopChan        chan struct{}
	targetChan      chan float64
	running         bool
	pulsesPerDegree float64
}

func NewMotorController(
	cfg *config.MotorConfig,
	serialPort serial.SerialPort,
	compass compass.Compass,
) *MotorController {
	return &MotorController{
		cfg:             cfg,
		serialPort:      serialPort,
		compass:         compass,
		currentAngle:    0,
		targetAngle:     0,
		status:          StatusAligned,
		targetChan:      make(chan float64, 5),
		pulsesPerDegree: 100,
	}
}

func (m *MotorController) Start(initialAngle float64) {
	m.mu.Lock()
	if m.running {
		m.mu.Unlock()
		return
	}
	m.running = true
	m.currentAngle = normalizeAngle(initialAngle)
	m.targetAngle = normalizeAngle(initialAngle)
	m.status = StatusAligned
	m.stopChan = make(chan struct{})
	m.mu.Unlock()

	log.Printf("[电机] 控制器已启动, 初始角度: %.2f°, 转速: %.2f°/s", m.currentAngle, m.cfg.SpeedDegPerSec)

	go m.controlLoop()
}

func (m *MotorController) Stop() {
	m.mu.Lock()
	if !m.running {
		m.mu.Unlock()
		return
	}
	m.running = false
	close(m.stopChan)
	m.mu.Unlock()

	log.Println("[电机] 控制器已停止")
}

func (m *MotorController) SetTargetAngle(angle float64) {
	normalized := normalizeAngle(angle)
	log.Printf("[电机] 收到目标角度指令: %.2f°", normalized)

	select {
	case m.targetChan <- normalized:
	default:
		log.Printf("[电机] 目标角度指令队列已满，丢弃旧指令")
		select {
		case <-m.targetChan:
		default:
		}
		m.targetChan <- normalized
	}
}

func (m *MotorController) GetCurrentAngle() float64 {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.currentAngle
}

func (m *MotorController) GetTargetAngle() float64 {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.targetAngle
}

func (m *MotorController) GetStatus() MotorStatus {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.status
}

func (m *MotorController) controlLoop() {
	updateTicker := time.NewTicker(100 * time.Millisecond)
	defer updateTicker.Stop()

	for {
		select {
		case <-m.stopChan:
			return
		case newTarget := <-m.targetChan:
			m.updateTarget(newTarget)
		case <-updateTicker.C:
			m.updateAngle()
		}
	}
}

func (m *MotorController) updateTarget(newTarget float64) {
	m.mu.Lock()
	defer m.mu.Unlock()

	m.targetAngle = newTarget
	diff := shortestAngleDiff(m.currentAngle, m.targetAngle)

	if math.Abs(diff) <= m.cfg.ToleranceDeg {
		m.status = StatusAligned
		log.Printf("[电机] 目标角度 %.2f° 与当前角度 %.2f° 已对齐", m.targetAngle, m.currentAngle)
	} else {
		m.status = StatusRotating
		dir := "顺时针"
		if diff < 0 {
			dir = "逆时针"
		}
		log.Printf("[电机] 开始旋转: %.2f° → %.2f° (差值: %.2f°, 方向: %s)",
			m.currentAngle, m.targetAngle, math.Abs(diff), dir)
	}
}

func (m *MotorController) updateAngle() {
	m.mu.Lock()
	defer m.mu.Unlock()

	if m.status != StatusRotating {
		return
	}

	diff := shortestAngleDiff(m.currentAngle, m.targetAngle)
	absDiff := math.Abs(diff)

	stepDeg := m.cfg.SpeedDegPerSec * 0.1

	if absDiff <= m.cfg.ToleranceDeg {
		m.currentAngle = m.targetAngle
		m.status = StatusAligned
		m.compass.SetAngle(m.currentAngle)
		log.Printf("[电机] 已到达目标角度: %.2f° (状态: %s)", m.currentAngle, m.status)
		return
	}

	if absDiff < stepDeg {
		stepDeg = absDiff
	}

	direction := 1
	if diff < 0 {
		direction = -1
	}

	m.currentAngle = normalizeAngle(m.currentAngle + float64(direction)*stepDeg)
	m.compass.SetAngle(m.currentAngle)

	pulses := int(math.Abs(float64(direction) * stepDeg * m.pulsesPerDegree))
	if pulses > 0 {
		if err := m.serialPort.SendPulse(pulses, direction); err != nil {
			log.Printf("[电机] 发送脉冲失败: %v", err)
		}
	}
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
