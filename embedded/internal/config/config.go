package config

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v3"
)

type SerialConfig struct {
	Port     string `yaml:"port"`
	BaudRate int    `yaml:"baud_rate"`
	DataBits int    `yaml:"data_bits"`
	StopBits int    `yaml:"stop_bits"`
	Parity   string `yaml:"parity"`
	UseMock  bool   `yaml:"use_mock"`
}

type BackendConfig struct {
	WSUrl string `yaml:"ws_url"`
}

type MotorConfig struct {
	SpeedDegPerSec float64 `yaml:"speed_deg_per_sec"`
	ToleranceDeg   float64 `yaml:"tolerance_deg"`
}

type ReportConfig struct {
	CurrentAngleIntervalMs int `yaml:"current_angle_interval_ms"`
	HeartbeatIntervalSec   int `yaml:"heartbeat_interval_sec"`
}

type Config struct {
	Serial   SerialConfig   `yaml:"serial"`
	DeviceID string         `yaml:"device_id"`
	Backend  BackendConfig  `yaml:"backend"`
	Motor    MotorConfig    `yaml:"motor"`
	Report   ReportConfig   `yaml:"report"`
}

func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件失败: %w", err)
	}

	var cfg Config
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %w", err)
	}

	if err := cfg.validate(); err != nil {
		return nil, err
	}

	return &cfg, nil
}

func (c *Config) validate() error {
	if c.DeviceID == "" {
		return fmt.Errorf("device_id 不能为空")
	}
	if c.Backend.WSUrl == "" {
		return fmt.Errorf("backend.ws_url 不能为空")
	}
	if c.Motor.SpeedDegPerSec <= 0 {
		return fmt.Errorf("motor.speed_deg_per_sec 必须大于0")
	}
	if c.Motor.ToleranceDeg <= 0 {
		return fmt.Errorf("motor.tolerance_deg 必须大于0")
	}
	if c.Report.CurrentAngleIntervalMs <= 0 {
		return fmt.Errorf("report.current_angle_interval_ms 必须大于0")
	}
	if c.Report.HeartbeatIntervalSec <= 0 {
		return fmt.Errorf("report.heartbeat_interval_sec 必须大于0")
	}
	return nil
}
