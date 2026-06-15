package compass

import (
	"log"
	"math"
	"sync"
	"time"
)

type Compass interface {
	Start()
	Stop()
	GetAngle() float64
	SetAngle(angle float64)
	AngleChannel() <-chan float64
}

type MockCompass struct {
	mu            sync.RWMutex
	currentAngle  float64
	targetAngle   float64
	stopChan      chan struct{}
	angleChan     chan float64
	running       bool
	noiseLevel    float64
	sampleRateMs  int
}

func NewMockCompass(initialAngle float64) *MockCompass {
	return &MockCompass{
		currentAngle: normalizeAngle(initialAngle),
		targetAngle:  normalizeAngle(initialAngle),
		noiseLevel:   0.05,
		sampleRateMs: 50,
		angleChan:    make(chan float64, 10),
	}
}

func (c *MockCompass) Start() {
	c.mu.Lock()
	if c.running {
		c.mu.Unlock()
		return
	}
	c.running = true
	c.stopChan = make(chan struct{})
	c.mu.Unlock()

	log.Printf("[罗盘][模拟] 罗盘已启动, 初始方位角: %.2f°", c.currentAngle)

	go c.sampleLoop()
}

func (c *MockCompass) Stop() {
	c.mu.Lock()
	if !c.running {
		c.mu.Unlock()
		return
	}
	c.running = false
	close(c.stopChan)
	c.mu.Unlock()

	log.Println("[罗盘][模拟] 罗盘已停止")
}

func (c *MockCompass) sampleLoop() {
	ticker := time.NewTicker(time.Duration(c.sampleRateMs) * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-c.stopChan:
			return
		case <-ticker.C:
			c.updateSample()
		}
	}
}

func (c *MockCompass) updateSample() {
	c.mu.Lock()
	angle := c.currentAngle
	c.mu.Unlock()

	noisyAngle := angle + (c.noiseLevel * (math.Floor(randFloat()*100)/100 - 0.5))
	noisyAngle = normalizeAngle(noisyAngle)

	select {
	case c.angleChan <- noisyAngle:
	default:
	}
}

func (c *MockCompass) GetAngle() float64 {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.currentAngle
}

func (c *MockCompass) SetAngle(angle float64) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.currentAngle = normalizeAngle(angle)
}

func (c *MockCompass) AngleChannel() <-chan float64 {
	return c.angleChan
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

func randFloat() float64 {
	return float64(time.Now().UnixNano()%1000) / 1000.0
}
