package serial

import (
	"fmt"
	"log"
	"sync"
	"time"

	"windturbine-embedded/internal/config"
)

type SerialPort interface {
	Open() error
	Close() error
	SendPulse(pulses int, direction int) error
}

type RealSerialPort struct {
	cfg *config.SerialConfig
}

func NewRealSerialPort(cfg *config.SerialConfig) *RealSerialPort {
	return &RealSerialPort{cfg: cfg}
}

func (r *RealSerialPort) Open() error {
	log.Printf("[串口] 正在打开真实串口: %s", r.cfg.Port)
	return fmt.Errorf("真实串口库 tarm/serial 未集成，使用模拟模式")
}

func (r *RealSerialPort) Close() error {
	log.Println("[串口] 真实串口已关闭")
	return nil
}

func (r *RealSerialPort) SendPulse(pulses int, direction int) error {
	log.Printf("[串口] 发送脉冲: %d, 方向: %d", pulses, direction)
	return nil
}

type MockSerialPort struct {
	cfg       *config.SerialConfig
	mu        sync.Mutex
	isOpen    bool
	pulseLog  []PulseRecord
}

type PulseRecord struct {
	Time      time.Time
	Pulses    int
	Direction int
}

func NewMockSerialPort(cfg *config.SerialConfig) *MockSerialPort {
	return &MockSerialPort{
		cfg:      cfg,
		pulseLog: make([]PulseRecord, 0),
	}
}

func (m *MockSerialPort) Open() error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if m.isOpen {
		return fmt.Errorf("串口已经打开")
	}

	m.isOpen = true
	log.Printf("[串口][模拟] 串口已打开: port=%s, baud=%d", m.cfg.Port, m.cfg.BaudRate)
	return nil
}

func (m *MockSerialPort) Close() error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.isOpen {
		return fmt.Errorf("串口未打开")
	}

	m.isOpen = false
	log.Printf("[串口][模拟] 串口已关闭，共发送 %d 次脉冲指令", len(m.pulseLog))
	return nil
}

func (m *MockSerialPort) SendPulse(pulses int, direction int) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.isOpen {
		return fmt.Errorf("串口未打开")
	}

	record := PulseRecord{
		Time:      time.Now(),
		Pulses:    pulses,
		Direction: direction,
	}
	m.pulseLog = append(m.pulseLog, record)

	dirStr := "顺时针"
	if direction < 0 {
		dirStr = "逆时针"
	}
	log.Printf("[串口][模拟] 发送脉冲: %d 步, 方向: %s", pulses, dirStr)

	time.Sleep(10 * time.Millisecond)
	return nil
}

func (m *MockSerialPort) GetPulseCount() int {
	m.mu.Lock()
	defer m.mu.Unlock()
	return len(m.pulseLog)
}

func NewSerialPort(cfg *config.SerialConfig) (SerialPort, error) {
	if cfg.UseMock {
		return NewMockSerialPort(cfg), nil
	}

	real := NewRealSerialPort(cfg)
	if err := real.Open(); err != nil {
		log.Printf("[串口] 真实串口打开失败，降级为模拟模式: %v", err)
		return NewMockSerialPort(cfg), nil
	}

	return real, nil
}
