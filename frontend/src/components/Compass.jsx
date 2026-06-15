import { useState, useRef, useCallback, useEffect } from 'react'

const Compass = ({ currentAngle, targetAngle, onTargetAngleChange, deviceStatus, online }) => {
  const svgRef = useRef(null)
  const [isDragging, setIsDragging] = useState(false)
  const [dragAngle, setDragAngle] = useState(null)

  const centerX = 200
  const centerY = 200
  const radius = 180

  const getAngleFromPoint = useCallback((clientX, clientY) => {
    if (!svgRef.current) return 0
    const rect = svgRef.current.getBoundingClientRect()
    const svgCenterX = rect.left + rect.width / 2
    const svgCenterY = rect.top + rect.height / 2
    const dx = clientX - svgCenterX
    const dy = clientY - svgCenterY
    let angle = Math.atan2(dx, -dy) * (180 / Math.PI)
    if (angle < 0) angle += 360
    return Math.round(angle)
  }, [])

  const handleMouseDown = useCallback((e) => {
    e.preventDefault()
    setIsDragging(true)
    const angle = getAngleFromPoint(e.clientX, e.clientY)
    setDragAngle(angle)
  }, [getAngleFromPoint])

  const handleMouseMove = useCallback((e) => {
    if (!isDragging) return
    const angle = getAngleFromPoint(e.clientX, e.clientY)
    setDragAngle(angle)
  }, [isDragging, getAngleFromPoint])

  const handleMouseUp = useCallback(() => {
    if (isDragging && dragAngle !== null) {
      let normalizedAngle = dragAngle
      if (normalizedAngle > 180) normalizedAngle -= 360
      onTargetAngleChange(normalizedAngle)
    }
    setIsDragging(false)
    setDragAngle(null)
  }, [isDragging, dragAngle, onTargetAngleChange])

  const handleTouchStart = useCallback((e) => {
    e.preventDefault()
    const touch = e.touches[0]
    setIsDragging(true)
    const angle = getAngleFromPoint(touch.clientX, touch.clientY)
    setDragAngle(angle)
  }, [getAngleFromPoint])

  const handleTouchMove = useCallback((e) => {
    if (!isDragging) return
    e.preventDefault()
    const touch = e.touches[0]
    const angle = getAngleFromPoint(touch.clientX, touch.clientY)
    setDragAngle(angle)
  }, [isDragging, getAngleFromPoint])

  const handleTouchEnd = useCallback(() => {
    if (isDragging && dragAngle !== null) {
      let normalizedAngle = dragAngle
      if (normalizedAngle > 180) normalizedAngle -= 360
      onTargetAngleChange(normalizedAngle)
    }
    setIsDragging(false)
    setDragAngle(null)
  }, [isDragging, dragAngle, onTargetAngleChange])

  useEffect(() => {
    if (isDragging) {
      window.addEventListener('mousemove', handleMouseMove)
      window.addEventListener('mouseup', handleMouseUp)
      window.addEventListener('touchmove', handleTouchMove, { passive: false })
      window.addEventListener('touchend', handleTouchEnd)
    }
    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
      window.removeEventListener('touchmove', handleTouchMove)
      window.removeEventListener('touchend', handleTouchEnd)
    }
  }, [isDragging, handleMouseMove, handleMouseUp, handleTouchMove, handleTouchEnd])

  const renderTicks = () => {
    const ticks = []
    for (let i = 0; i < 360; i += 15) {
      const isMajor = i % 30 === 0
      const tickLength = isMajor ? 20 : 10
      const tickWidth = isMajor ? 3 : 1.5
      const angle = (i - 90) * (Math.PI / 180)
      const x1 = centerX + Math.cos(angle) * (radius - tickLength)
      const y1 = centerY + Math.sin(angle) * (radius - tickLength)
      const x2 = centerX + Math.cos(angle) * radius
      const y2 = centerY + Math.sin(angle) * radius
      ticks.push(
        <line
          key={i}
          x1={x1}
          y1={y1}
          x2={x2}
          y2={y2}
          stroke={isMajor ? '#7dd3fc' : '#38bdf8'}
          strokeWidth={tickWidth}
          opacity={isMajor ? 1 : 0.6}
        />
      )
    }
    return ticks
  }

  const renderDegrees = () => {
    const degrees = []
    for (let i = 0; i < 360; i += 30) {
      const angle = (i - 90) * (Math.PI / 180)
      const x = centerX + Math.cos(angle) * (radius - 40)
      const y = centerY + Math.sin(angle) * (radius - 40)
      degrees.push(
        <text
          key={i}
          x={x}
          y={y}
          fill="#bae6fd"
          fontSize="14"
          fontWeight="bold"
          textAnchor="middle"
          dominantBaseline="middle"
        >
          {i}°
        </text>
      )
    }
    return degrees
  }

  const renderDirections = () => {
    const directions = [
      { label: 'N', angle: 0 },
      { label: 'E', angle: 90 },
      { label: 'S', angle: 180 },
      { label: 'W', angle: 270 },
    ]
    return directions.map((dir) => {
      const angle = (dir.angle - 90) * (Math.PI / 180)
      const x = centerX + Math.cos(angle) * (radius - 70)
      const y = centerY + Math.sin(angle) * (radius - 70)
      const isCardinal = dir.label === 'N'
      return (
        <text
          key={dir.label}
          x={x}
          y={y}
          fill={isCardinal ? '#f97316' : '#38bdf8'}
          fontSize={isCardinal ? '22' : '18'}
          fontWeight="bold"
          textAnchor="middle"
          dominantBaseline="middle"
        >
          {dir.label}
        </text>
      )
    })
  }

  const renderPointer = (angle, color, label, isDraggable) => {
    const displayAngle = angle != null ? (angle < 0 ? angle + 360 : angle) : 0
    const rad = (displayAngle - 90) * (Math.PI / 180)
    const tipX = centerX + Math.cos(rad) * (radius - 30)
    const tipY = centerY + Math.sin(rad) * (radius - 30)
    const baseX1 = centerX + Math.cos(rad + 2.6) * 20
    const baseY1 = centerY + Math.sin(rad + 2.6) * 20
    const baseX2 = centerX + Math.cos(rad - 2.6) * 20
    const baseY2 = centerY + Math.sin(rad - 2.6) * 20

    return (
      <g
        style={isDraggable ? { cursor: 'grab' } : {}}
        onMouseDown={isDraggable ? handleMouseDown : undefined}
        onTouchStart={isDraggable ? handleTouchStart : undefined}
      >
        <polygon
          points={`${tipX},${tipY} ${baseX1},${baseY1} ${baseX2},${baseY2}`}
          fill={color}
          stroke="#fff"
          strokeWidth="2"
          style={{ filter: `drop-shadow(0 0 8px ${color})` }}
        />
        <circle cx={centerX} cy={centerY} r="8" fill={color} stroke="#fff" strokeWidth="2" />
        {label && (
          <text
            x={tipX}
            y={tipY - 15}
            fill={color}
            fontSize="12"
            fontWeight="bold"
            textAnchor="middle"
          >
            {label}
          </text>
        )}
      </g>
    )
  }

  const displayTargetAngle = dragAngle !== null ? dragAngle : (targetAngle != null ? (targetAngle < 0 ? targetAngle + 360 : targetAngle) : null)
  const displayCurrentAngle = currentAngle != null ? (currentAngle < 0 ? currentAngle + 360 : currentAngle) : null

  return (
    <div className="flex flex-col items-center">
      <div className="relative">
        <svg
          ref={svgRef}
          width="400"
          height="400"
          viewBox="0 0 400 400"
          className="select-none"
          style={{ touchAction: 'none' }}
        >
          <defs>
            <radialGradient id="compassBg" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor="#0c4a6e" />
              <stop offset="70%" stopColor="#082f49" />
              <stop offset="100%" stopColor="#0c4a6e" />
            </radialGradient>
            <filter id="glow">
              <feGaussianBlur stdDeviation="3" result="coloredBlur" />
              <feMerge>
                <feMergeNode in="coloredBlur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>

          <circle cx={centerX} cy={centerY} r={radius + 10} fill="none" stroke="#0ea5e9" strokeWidth="2" opacity="0.3" />
          <circle cx={centerX} cy={centerY} r={radius + 5} fill="none" stroke="#38bdf8" strokeWidth="1" opacity="0.5" />
          <circle cx={centerX} cy={centerY} r={radius} fill="url(#compassBg)" stroke="#0ea5e9" strokeWidth="2" filter="url(#glow)" />

          {renderTicks()}
          {renderDegrees()}
          {renderDirections()}

          <circle cx={centerX} cy={centerY} r="50" fill="none" stroke="#38bdf8" strokeWidth="1" opacity="0.3" strokeDasharray="4 4" />
          <circle cx={centerX} cy={centerY} r="100" fill="none" stroke="#38bdf8" strokeWidth="1" opacity="0.2" strokeDasharray="4 4" />

          {currentAngle != null && online && renderPointer(displayCurrentAngle, '#22c55e', null, false)}
          {targetAngle != null && renderPointer(displayTargetAngle, '#ef4444', null, true)}
          {targetAngle == null && !isDragging && renderPointer(0, '#ef4444', null, true)}
        </svg>
      </div>

      <div className="mt-6 grid grid-cols-2 gap-6 w-full max-w-md">
        <div className="glass-panel p-4 text-center">
          <div className="text-ocean-300 text-sm mb-1">当前角度</div>
          <div className="text-green-400 text-3xl font-bold font-mono">
            {displayCurrentAngle != null ? `${displayCurrentAngle.toFixed(1)}°` : '--'}
          </div>
        </div>
        <div className="glass-panel p-4 text-center">
          <div className="text-ocean-300 text-sm mb-1">目标角度</div>
          <div className="text-red-400 text-3xl font-bold font-mono">
            {displayTargetAngle != null ? `${displayTargetAngle.toFixed(1)}°` : '--'}
          </div>
        </div>
      </div>

      <div className="mt-4 glass-panel px-6 py-3 flex items-center gap-3">
        <span
          className={`w-3 h-3 rounded-full ${
            !online ? 'bg-gray-500' : deviceStatus === 'rotating' ? 'bg-yellow-400 pulse-dot' : deviceStatus === 'aligned' ? 'bg-green-400' : 'bg-gray-500'
          }`}
        />
        <span className="text-ocean-100 font-medium">
          {!online ? '离线' : deviceStatus === 'rotating' ? '旋转中...' : deviceStatus === 'aligned' ? '已对齐' : '未知'}
        </span>
      </div>

      <div className="mt-4 text-ocean-300 text-sm text-center">
        拖动红色箭头设置目标角度
      </div>
    </div>
  )
}

export default Compass
