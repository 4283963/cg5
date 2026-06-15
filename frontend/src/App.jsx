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
  const [windSpeed, setWindSpeed] = useState(null)
  const [windDirection, setWindDirection] = useState(null)
  const [protectionEnabled, setProtectionEnabled] = useState(true)
  const [protectionActive, setProtectionActive] = useState(false)
  const [autoUnloadEnabled, setAutoUnloadEnabled] = useState(true)
  const [protectionMessage, setProtectionMessage] = useState('')
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
        fetch(`${API_URL}/protection/${DEVICE_ID}/status`)
          .then(r => r.json())
          .then(data => {
            if (data.code === 0 && data.data) {
              setProtectionEnabled(data.data.protectionEnabled ?? true)
              setProtectionActive(data.data.protectionActive ?? false)
              setAutoUnloadEnabled(data.data.autoUnloadEnabled ?? true)
            }
          })
          .catch(e => console.warn('获取保护状态失败:', e))
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
          } else if (message.type === 'WIND_SPEED_REPORT' && message.deviceId === DEVICE_ID) {
            const speed = parseFloat(message.windSpeed ?? message.data)
            const dir = parseFloat(message.windDirection ?? 0)
            if (!isNaN(speed)) {
              setWindSpeed(speed)
              setWindDirection(dir)
            }
          } else if (message.type === 'PROTECTION_STATUS' && message.deviceId === DEVICE_ID) {
            if (message.protectionEnabled !== undefined) {
              setProtectionEnabled(message.protectionEnabled)
            }
            if (message.protectionActive !== undefined) {
              setProtectionActive(message.protectionActive)
            }
            if (message.autoUnloadEnabled !== undefined) {
              setAutoUnloadEnabled(message.autoUnloadEnabled)
            }
            if (message.currentWindSpeed !== undefined && !isNaN(parseFloat(message.currentWindSpeed))) {
              setWindSpeed(parseFloat(message.currentWindSpeed))
            }
            if (message.reason) {
              setProtectionMessage(message.reason)
              if (message.protectionActive) {
                setTimeout(() => setProtectionMessage(''), 5000)
              }
            }
          } else if (message.type === 'SAFE_SET_ANGLE' && message.deviceId === DEVICE_ID) {
            const optimized = parseFloat(message.optimizedTargetAngle ?? message.targetAngle ?? message.data)
            if (!isNaN(optimized)) {
              setTargetAngle(optimized)
            }
          } else if (message.type === 'SET_ANGLE' && message.deviceId === DEVICE_ID) {
            const angle = parseFloat(message.angle ?? message.targetAngle ?? message.data)
            if (!isNaN(angle)) {
              setTargetAngle(angle)
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
    if (protectionActive) {
      console.warn('强风保护中，手动控制已锁定')
      return
    }
    try {
      const response = await fetch(`${API_URL}/yaw/${DEVICE_ID}/angle`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ angle }),
      })
      if (response.ok) {
        const result = await response.json()
        if (result.code === 0) {
          setTargetAngle(angle)
        } else {
          console.error('设置角度失败:', result.message)
        }
      } else {
        console.error('Failed to set target angle')
      }
    } catch (err) {
      console.error('Failed to send target angle:', err)
    }
  }, [protectionActive])

  const toggleProtection = useCallback(async () => {
    const newEnabled = !protectionEnabled
    const endpoint = newEnabled ? 'enable' : 'disable'
    try {
      const response = await fetch(`${API_URL}/protection/${DEVICE_ID}/${endpoint}`, {
        method: 'PUT',
      })
      const result = await response.json()
      if (result.code === 0) {
        setProtectionEnabled(newEnabled)
      } else {
        console.warn('切换保护状态失败:', result.message)
      }
    } catch (err) {
      console.error('Failed to toggle protection:', err)
    }
  }, [protectionEnabled])

  const toggleAutoUnload = useCallback(async () => {
    try {
      const response = await fetch(`${API_URL}/protection/${DEVICE_ID}/config`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          autoUnloadEnabled: !autoUnloadEnabled,
        }),
      })
      const result = await response.json()
      if (result.code === 0) {
        setAutoUnloadEnabled(!autoUnloadEnabled)
      } else {
        console.warn('切换自动卸载失败:', result.message)
      }
    } catch (err) {
      console.error('Failed to toggle auto unload:', err)
    }
  }, [autoUnloadEnabled])

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

        {windSpeed !== null && (
          <div className="flex gap-4 mb-6 justify-center">
            <div className="bg-ocean-800/50 backdrop-blur border border-ocean-600/50 rounded-xl px-6 py-3">
              <div className="text-ocean-300 text-sm mb-1">实时风速</div>
              <div className={`text-3xl font-bold ${windSpeed >= 25 ? 'text-red-400' : windSpeed >= 15 ? 'text-amber-400' : 'text-white'}`}>
                {windSpeed.toFixed(1)} <span className="text-lg">m/s</span>
              </div>
              {windSpeed >= 25 && (
                <div className="text-red-400 text-xs mt-1 animate-pulse">🌪️ 大风预警！</div>
              )}
            </div>
            {windDirection !== null && (
              <div className="bg-ocean-800/50 backdrop-blur border border-ocean-600/50 rounded-xl px-6 py-3">
                <div className="text-ocean-300 text-sm mb-1">当前风向</div>
                <div className="text-3xl font-bold text-white">
                  {windDirection.toFixed(0)} <span className="text-lg">°</span>
                </div>
              </div>
            )}
          </div>
        )}

        <div className="flex gap-3 mb-6 justify-center">
          <button
            onClick={toggleProtection}
            disabled={protectionActive}
            className={`px-5 py-2.5 rounded-lg font-medium transition-all ${
              protectionActive
                ? 'bg-gray-600 cursor-not-allowed text-gray-400'
                : protectionEnabled
                ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                : 'bg-gray-600 hover:bg-gray-700 text-white'
            }`}
          >
            {protectionActive ? '保护已激活' : protectionEnabled ? '✓ 保护开启' : '✗ 保护关闭'}
          </button>
          <button
            onClick={toggleAutoUnload}
            disabled={!protectionEnabled || protectionActive}
            className={`px-5 py-2.5 rounded-lg font-medium transition-all ${
              !protectionEnabled || protectionActive
                ? 'bg-gray-600 cursor-not-allowed text-gray-400'
                : autoUnloadEnabled
                ? 'bg-blue-600 hover:bg-blue-700 text-white'
                : 'bg-gray-600 hover:bg-gray-700 text-white'
            }`}
          >
            {autoUnloadEnabled ? '✓ 自动卸载' : '✗ 自动卸载'}
          </button>
        </div>

        {protectionActive && protectionMessage && (
          <div className="mb-4 px-6 py-3 bg-red-500/20 border border-red-500 rounded-lg text-red-300 font-medium animate-pulse text-center">
            {protectionMessage}
          </div>
        )}

        <div className="glass-panel p-8 shadow-glow-lg">
          <Compass
            currentAngle={currentAngle}
            targetAngle={targetAngle}
            onTargetAngleChange={sendTargetAngle}
            deviceStatus={getDeviceStatus()}
            online={online}
            protectionActive={protectionActive}
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
