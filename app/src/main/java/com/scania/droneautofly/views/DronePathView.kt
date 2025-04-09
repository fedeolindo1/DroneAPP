package com.scania.droneautofly.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.scania.droneautofly.R
import kotlin.math.max
import kotlin.math.min

/**
 * Visualização customizada que mostra o caminho de voo do drone com animação
 */
class DronePathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Cores da Scania
    private val primaryColor = context.getColor(R.color.scania_blue)
    private val secondaryColor = context.getColor(R.color.scania_red)
    private val pathColor = context.getColor(R.color.scania_blue_light)
    private val targetPointColor = context.getColor(R.color.scania_grey)
    private val homePointColor = context.getColor(R.color.scania_red)
    
    // Configurações de pintura
    private val pathPaint = Paint().apply {
        color = pathColor
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }
    
    private val dronePaint = Paint().apply {
        color = primaryColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val droneOutlinePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    private val targetPaint = Paint().apply {
        color = targetPointColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val homePaint = Paint().apply {
        color = homePointColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    
    // Dados de caminho
    private val flightPath = Path()
    private val waypoints = mutableListOf<PointF>()
    private val targetPoints = mutableListOf<PointF>()
    private var homePoint: PointF? = null
    private var currentDronePosition = PointF(0f, 0f)
    private var droneRotation = 0f
    
    // Animação
    private var animationProgress = 0f
    private var pathAnimator: ValueAnimator? = null
    private var isAnimating = false
    
    // Limites do mapa
    private var minX = Float.MAX_VALUE
    private var maxX = Float.MIN_VALUE
    private var minY = Float.MAX_VALUE
    private var maxY = Float.MIN_VALUE
    private var padding = 50f
    
    init {
        // Exemplo de caminho (seria substituído por dados reais)
        setHomePoint(150f, 150f)
        addWaypoint(300f, 200f)
        addWaypoint(400f, 150f)
        addWaypoint(450f, 300f)
        addWaypoint(250f, 400f)
        addWaypoint(150f, 250f)
        addTargetPoint(300f, 150f)
        addTargetPoint(400f, 300f)
        
        // Iniciar com o drone na posição inicial
        homePoint?.let {
            currentDronePosition.set(it.x, it.y)
        }
    }
    
    /**
     * Define o ponto inicial (home) do drone
     */
    fun setHomePoint(x: Float, y: Float) {
        homePoint = PointF(x, y)
        updateBounds(x, y)
        invalidate()
    }
    
    /**
     * Adiciona um ponto no caminho de voo
     */
    fun addWaypoint(x: Float, y: Float) {
        waypoints.add(PointF(x, y))
        updateBounds(x, y)
        updatePath()
        invalidate()
    }
    
    /**
     * Adiciona um ponto alvo (local de código de barras)
     */
    fun addTargetPoint(x: Float, y: Float) {
        targetPoints.add(PointF(x, y))
        updateBounds(x, y)
        invalidate()
    }
    
    /**
     * Limpa todos os pontos
     */
    fun clearPath() {
        waypoints.clear()
        targetPoints.clear()
        homePoint = null
        flightPath.reset()
        stopAnimation()
        invalidate()
    }
    
    /**
     * Atualiza os limites do mapa para garantir que todos os pontos sejam visíveis
     */
    private fun updateBounds(x: Float, y: Float) {
        minX = min(minX, x)
        maxX = max(maxX, x)
        minY = min(minY, y)
        maxY = max(maxY, y)
    }
    
    /**
     * Atualiza o caminho de voo com base nos waypoints
     */
    private fun updatePath() {
        flightPath.reset()
        
        if (waypoints.isEmpty()) return
        
        homePoint?.let {
            flightPath.moveTo(it.x, it.y)
        } ?: run {
            flightPath.moveTo(waypoints[0].x, waypoints[0].y)
        }
        
        waypoints.forEach {
            flightPath.lineTo(it.x, it.y)
        }
        
        // Fechamos o caminho voltando ao home point
        homePoint?.let {
            flightPath.lineTo(it.x, it.y)
        }
    }
    
    /**
     * Inicia a animação do drone percorrendo o caminho
     */
    fun startAnimation() {
        if (waypoints.isEmpty() || isAnimating) return
        
        stopAnimation()
        
        pathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 10000 // 10 segundos para percorrer todo o caminho
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                animationProgress = animator.animatedValue as Float
                updateDronePosition(animationProgress)
                invalidate()
            }
            start()
        }
        
        isAnimating = true
    }
    
    /**
     * Para a animação
     */
    fun stopAnimation() {
        pathAnimator?.cancel()
        pathAnimator = null
        isAnimating = false
    }
    
    /**
     * Atualiza a posição do drone com base no progresso da animação
     */
    private fun updateDronePosition(progress: Float) {
        if (waypoints.isEmpty()) return
        
        val points = mutableListOf<PointF>()
        homePoint?.let { points.add(it) }
        points.addAll(waypoints)
        homePoint?.let { points.add(it) }
        
        if (points.size < 2) return
        
        val totalSegments = points.size - 1
        val segmentProgress = progress * totalSegments
        val currentSegment = segmentProgress.toInt()
        val segmentFraction = segmentProgress - currentSegment
        
        if (currentSegment < totalSegments) {
            val startPoint = points[currentSegment]
            val endPoint = points[currentSegment + 1]
            
            currentDronePosition.x = startPoint.x + (endPoint.x - startPoint.x) * segmentFraction
            currentDronePosition.y = startPoint.y + (endPoint.y - startPoint.y) * segmentFraction
            
            // Calcular rotação para que o drone "olhe" na direção do movimento
            val dx = endPoint.x - startPoint.x
            val dy = endPoint.y - startPoint.y
            droneRotation = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val scaleX = (width - 2 * padding) / (maxX - minX)
        val scaleY = (height - 2 * padding) / (maxY - minY)
        val scale = min(scaleX, scaleY)
        
        canvas.save()
        canvas.translate(padding, padding)
        canvas.scale(scale, scale)
        canvas.translate(-minX, -minY)
        
        // Desenhar o caminho de voo
        canvas.drawPath(flightPath, pathPaint)
        
        // Desenhar pontos alvo (locais de código de barras)
        targetPoints.forEach { point ->
            canvas.drawCircle(point.x, point.y, 15f, targetPaint)
            canvas.drawText("B", point.x, point.y + 10f, textPaint)
        }
        
        // Desenhar ponto inicial
        homePoint?.let {
            canvas.drawCircle(it.x, it.y, 15f, homePaint)
            canvas.drawText("H", it.x, it.y + 10f, textPaint)
        }
        
        // Desenhar drone
        canvas.save()
        canvas.translate(currentDronePosition.x, currentDronePosition.y)
        canvas.rotate(droneRotation)
        
        // Desenhar o triângulo que representa o drone
        val path = Path()
        path.moveTo(0f, -20f)  // Ponta superior
        path.lineTo(-15f, 15f) // Ponta inferior esquerda
        path.lineTo(15f, 15f)  // Ponta inferior direita
        path.close()           // Fecha o caminho (triângulo)
        
        canvas.drawPath(path, dronePaint)
        canvas.drawPath(path, droneOutlinePaint)
        canvas.restore()
        
        canvas.restore()
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}