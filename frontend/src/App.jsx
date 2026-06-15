import { useState, useEffect, useCallback, useRef } from 'react'
import Compass from './components/Compass'

const DEVICE_ID = 'device001'
const API_URL = '/api'
const ALIGN_THRESHOLD = 2

function getWsUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/admin`
}

function App() {
  const [currentAngle, setCurrentAngle] = useState(null)
  const [targetAngle, setTargetAngle] = useState(null)
  const [online, setOnline] = useState(false)
  const [wsConnected, setWsConnected] = useState(false)
  const wsRef = useRef(null)
  const reconnectTimerRef = useRef(null)

  const getDeviceStatus = useCallback(() => {
    if (!online) return 'offline'
    if (currentAngle == null || targetAngle == null) return 'unknown'
    const diff = Math.abs(currentAngle - targetAngle)
    const normalizedDiff = diff > 180 ? 360 - diff : diff
    return normalizedDiff <= ALIGN_THRESHOLD ? 'aligned' : 'rotating'
  }, [online, currentAngle, targetAngle])

  const connectWebSocket = useCallback(() => {
    try {
      const wsUrl = getWsUrl()
      const ws = new WebSocket(wsUrl)
      wsRef.current = ws

      ws.onopen = () => {
        console.log('WebSocket connected')
        setWsConnected(true)
      }

      ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data)
          if (message.type === 'STATUS_REPORT' && message.deviceId === DEVICE_ID) {
            const angle = parseFloat(message.data)
            if (!isNaN(angle)) {
              setCurrentAngle(angle)
              setOnline(true)
            }
          }
        } catch (err) {
          console.error('Failed to parse WebSocket message:', err)
        }
      }

      ws.onclose = () => {
        console.log('WebSocket disconnected')
        setWsConnected(false)
        setOnline(false)
        scheduleReconnect()
      }

      ws.onerror = (error) => {
        console.error('WebSocket error:', error)
        setWsConnected(false)
      }
    } catch (err) {
      console.error('Failed to create WebSocket:', err)
      scheduleReconnect()
    }
  }, [])

  const scheduleReconnect = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current)
    }
    reconnectTimerRef.current = setTimeout(() => {
      console.log('Attempting to reconnect WebSocket...')
      connectWebSocket()
    }, 3000)
  }, [connectWebSocket])

  const fetchDeviceStatus = useCallback(async () => {
    try {
      const response = await fetch(`${API_URL}/yaw/${DEVICE_ID}/status`)
      if (response.ok) {
        const result = await response.json()
        if (result.code === 200 && result.data) {
          if (result.data.currentAngle != null) {
            setCurrentAngle(result.data.currentAngle)
          }
          if (result.data.targetAngle != null) {
            setTargetAngle(result.data.targetAngle)
          }
          setOnline(result.data.online === true)
        }
      }
    } catch (err) {
      console.error('Failed to fetch device status:', err)
    }
  }, [])

  const sendTargetAngle = useCallback(async (angle) => {
    try {
      const response = await fetch(`${API_URL}/yaw/${DEVICE_ID}/angle`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ angle }),
      })
      if (response.ok) {
        setTargetAngle(angle)
      } else {
        console.error('Failed to set target angle')
      }
    } catch (err) {
      console.error('Failed to send target angle:', err)
    }
  }, [])

  useEffect(() => {
    connectWebSocket()
    fetchDeviceStatus()

    const statusInterval = setInterval(() => {
      if (!wsConnected) {
        fetchDeviceStatus()
      }
    }, 5000)

    return () => {
      if (wsRef.current) {
        wsRef.current.close()
      }
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current)
      }
      clearInterval(statusInterval)
    }
  }, [connectWebSocket, fetchDeviceStatus, wsConnected])

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-8">
      <div className="w-full max-w-2xl">
        <header className="text-center mb-8">
          <h1 className="text-4xl font-bold text-ocean-100 mb-2 tracking-wider">
            CG5 罗盘控制系统
          </h1>
          <p className="text-ocean-400 text-lg">
            Yaw Angle Control System
          </p>
          <div className="mt-3 flex items-center justify-center gap-2">
            <span
              className={`w-2 h-2 rounded-full ${
                wsConnected ? 'bg-green-400 pulse-dot' : 'bg-red-400'
              }`}
            />
            <span className="text-sm text-ocean-300">
              WebSocket: {wsConnected ? '已连接' : '未连接'}
            </span>
            <span className="mx-2 text-ocean-600">|</span>
            <span className="text-sm text-ocean-300">
              设备 ID: {DEVICE_ID}
            </span>
          </div>
        </header>

        <div className="glass-panel p-8 shadow-glow-lg">
          <Compass
            currentAngle={currentAngle}
            targetAngle={targetAngle}
            onTargetAngleChange={sendTargetAngle}
            deviceStatus={getDeviceStatus()}
            online={online}
          />
        </div>

        <footer className="mt-8 text-center text-ocean-500 text-sm">
          拖动红色指针设置目标方向 · 绿色指针显示当前方向
        </footer>
      </div>
    </div>
  )
}

export default App
