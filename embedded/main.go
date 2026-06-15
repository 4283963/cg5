package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	"windturbine-embedded/internal/anemometer"
	"windturbine-embedded/internal/compass"
	"windturbine-embedded/internal/config"
	"windturbine-embedded/internal/motor"
	"windturbine-embedded/internal/serial"
	"windturbine-embedded/internal/wsclient"
)

func main() {
	configPath := flag.String("config", "config.yaml", "配置文件路径")
	initialAngle := flag.Float64("angle", 0.0, "初始方位角（度）")
	initialWindSpeed := flag.Float64("wind", 8.0, "初始风速（m/s）")
	initialWindDir := flag.Float64("winddir", 0.0, "初始风向（度）")
	stormMode := flag.Bool("storm", false, "模拟台风模式")
	flag.Parse()

	log.SetOutput(os.Stdout)
	log.SetFlags(log.Ldate | log.Ltime | log.Lmicroseconds)

	log.Println("========================================")
	log.Println("  风力涡轮机嵌入式控制端 启动中")
	log.Println("========================================")

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}
	log.Printf("配置加载成功: device_id=%s, 串口模式: mock=%v", cfg.DeviceID, cfg.Serial.UseMock)

	serialPort, err := serial.NewSerialPort(&cfg.Serial)
	if err != nil {
		log.Fatalf("初始化串口失败: %v", err)
	}
	if err := serialPort.Open(); err != nil {
		log.Fatalf("打开串口失败: %v", err)
	}
	defer serialPort.Close()

	compassSensor := compass.NewMockCompass(*initialAngle)
	compassSensor.Start()
	defer compassSensor.Stop()

	windSensor := anemometer.NewMockAnemometer(*initialWindSpeed, *initialWindDir)
	windSensor.Start()
	defer windSensor.Stop()

	if *stormMode {
		windSensor.SimulateStorm(true)
	}

	motorCtrl := motor.NewMotorController(&cfg.Motor, serialPort, compassSensor)
	motorCtrl.Start(*initialAngle)
	defer motorCtrl.Stop()

	ws := wsclient.NewWSClient(cfg, motorCtrl, windSensor)
	ws.Start()
	defer ws.Stop()

	log.Println("========================================")
	log.Println("  系统启动完成，等待指令...")
	log.Println("========================================")
	printStatus(cfg, motorCtrl, *initialAngle, windSensor)

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	sig := <-sigChan

	log.Printf("收到信号 %v，正在优雅退出...", sig)
	log.Println("系统已停止")
}

func printStatus(cfg *config.Config, motorCtrl *motor.MotorController, initialAngle float64, windSensor anemometer.Anemometer) {
	log.Println("-------- 运行状态 --------")
	log.Printf("  设备ID:        %s", cfg.DeviceID)
	log.Printf("  后端地址:      %s/%s", cfg.Backend.WSUrl, cfg.DeviceID)
	log.Printf("  初始角度:      %.2f°", initialAngle)
	log.Printf("  电机转速:      %.2f°/s", cfg.Motor.SpeedDegPerSec)
	log.Printf("  定位容差:      %.2f°", cfg.Motor.ToleranceDeg)
	log.Printf("  角度上报间隔:  %dms", cfg.Report.CurrentAngleIntervalMs)
	log.Printf("  心跳上报间隔:  %ds", cfg.Report.HeartbeatIntervalSec)
	log.Printf("  风速上报间隔:  %dms", cfg.Report.WindSpeedIntervalMs)
	log.Printf("  串口模式:      %s", getSerialMode(cfg.Serial.UseMock))
	log.Printf("  当前风速:      %.1f m/s", windSensor.GetWindSpeed())
	log.Printf("  当前风向:      %.1f°", windSensor.GetWindDirection())
	log.Println("--------------------------")
	fmt.Println()
}

func getSerialMode(useMock bool) string {
	if useMock {
		return "模拟模式"
	}
	return "真实硬件模式"
}
