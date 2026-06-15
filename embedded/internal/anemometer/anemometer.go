package anemometer

import (
	"log"
	"math"
	"math/rand"
	"sync"
	"time"
)

type Anemometer interface {
	Start()
	Stop()
	GetWindSpeed() float64
	GetWindDirection() float64
	WindSpeedChan() <-chan WindData
	SetWindSpeed(speed float64)
	SetWindDirection(direction float64)
}

type WindData struct {
	Timestamp time.Time
	Speed     float64
	Direction float64
}

type MockAnemometer struct {
	mu             sync.RWMutex
	stopChan       chan struct{}
	running        bool
	windSpeed      float64
	windDirection  float64
	baseSpeed      float64
	baseDirection  float64
	windSpeedChan  chan WindData
	simulateStorm  bool
}

func NewMockAnemometer(initialSpeed float64, initialDirection float64) *MockAnemometer {
	return &MockAnemometer{
		windSpeed:     initialSpeed,
		windDirection: initialDirection,
		baseSpeed:     initialSpeed,
		baseDirection: initialDirection,
		windSpeedChan: make(chan WindData, 10),
	}
}

func (a *MockAnemometer) Start() {
	a.mu.Lock()
	if a.running {
		a.mu.Unlock()
		return
	}
	a.running = true
	a.stopChan = make(chan struct{})
	a.mu.Unlock()

	log.Printf("[风速传感器] 已启动, 初始风速: %.1f m/s, 风向: %.1f°", a.windSpeed, a.windDirection)

	go a.simulateWind()
}

func (a *MockAnemometer) Stop() {
	a.mu.Lock()
	if !a.running {
		a.mu.Unlock()
		return
	}
	a.running = false
	close(a.stopChan)
	a.mu.Unlock()

	log.Println("[风速传感器] 已停止")
}

func (a *MockAnemometer) simulateWind() {
	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-a.stopChan:
			return
		case <-ticker.C:
			a.updateWind()
		}
	}
}

func (a *MockAnemometer) updateWind() {
	a.mu.Lock()
	defer a.mu.Unlock()

	if a.simulateStorm {
		a.baseSpeed += rand.Float64() * 2.0
		if a.baseSpeed > 45.0 {
			a.baseSpeed = 45.0
		}
	} else {
		a.baseSpeed += (rand.Float64() - 0.5) * 1.0
		if a.baseSpeed < 0 {
			a.baseSpeed = 0
		}
		if a.baseSpeed > 30.0 {
			a.baseSpeed = 30.0
		}
	}

	a.windSpeed = a.baseSpeed + (rand.Float64()-0.5)*2.0
	if a.windSpeed < 0 {
		a.windSpeed = 0
	}

	a.baseDirection += (rand.Float64() - 0.5) * 5.0
	a.windDirection = normalizeAngle(a.baseDirection)

	select {
	case a.windSpeedChan <- WindData{
		Timestamp: time.Now(),
		Speed:     math.Round(a.windSpeed*10) / 10,
		Direction: math.Round(a.windDirection*10) / 10,
	}:
	default:
	}
}

func (a *MockAnemometer) GetWindSpeed() float64 {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return math.Round(a.windSpeed*10) / 10
}

func (a *MockAnemometer) GetWindDirection() float64 {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return math.Round(a.windDirection*10) / 10
}

func (a *MockAnemometer) WindSpeedChan() <-chan WindData {
	return a.windSpeedChan
}

func (a *MockAnemometer) SetWindSpeed(speed float64) {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.baseSpeed = speed
	a.windSpeed = speed
	log.Printf("[风速传感器] 手动设置风速: %.1f m/s", speed)
}

func (a *MockAnemometer) SetWindDirection(direction float64) {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.baseDirection = normalizeAngle(direction)
	a.windDirection = a.baseDirection
	log.Printf("[风速传感器] 手动设置风向: %.1f°", a.windDirection)
}

func (a *MockAnemometer) SimulateStorm(enable bool) {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.simulateStorm = enable
	if enable {
		a.baseSpeed = 20.0
		log.Println("[风速传感器] 开始模拟台风天气！")
	} else {
		log.Println("[风速传感器] 台风模拟已结束")
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
