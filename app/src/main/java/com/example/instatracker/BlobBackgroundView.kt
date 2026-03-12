package com.example.instatracker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Background blobs for survey screens.
 * Solid pastel irregular forms (no gradients) to mirror the app landing screen.
 */
class BlobBackgroundView(
    context: Context,
    private val palette: Palette = Palette.PRE
) : View(context) {

    enum class Palette { PRE, POST }

    private data class Blob(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val color: Int,
        val alpha: Int,
        val speed: Long,
        val phaseOffset: Float
    )

    private val blobs: List<Blob>
    private val blobPaints: List<Paint>
    private val blobPaths: List<Path> = List(4) { Path() }
    private val animProgress = FloatArray(4) { 0f }
    private val animators = mutableListOf<ValueAnimator>()
    private val density = context.resources.displayMetrics.density

    init {
        blobs = when (palette) {
            Palette.PRE -> listOf(
                Blob(0.12f, 0.26f, 160f, Color.parseColor("#8B5CF6"), 72, 16500L, 0.0f),
                Blob(0.82f, 0.13f, 140f, Color.parseColor("#6366F1"), 68, 14500L, 1.2f),
                Blob(0.66f, 0.48f, 132f, Color.parseColor("#34D399"), 62, 17500L, 2.5f),
                Blob(0.18f, 0.80f, 124f, Color.parseColor("#FBBF24"), 66, 15500L, 0.7f)
            )
            Palette.POST -> listOf(
                Blob(0.10f, 0.20f, 160f, Color.parseColor("#9B6FCC"), 72, 17000L, 0.6f),
                Blob(0.80f, 0.15f, 138f, Color.parseColor("#C4973A"), 64, 14800L, 1.8f),
                Blob(0.64f, 0.53f, 130f, Color.parseColor("#34D399"), 58, 18200L, 2.9f),
                Blob(0.20f, 0.82f, 126f, Color.parseColor("#6B3FA0"), 60, 16000L, 0.4f)
            )
        }

        blobPaints = blobs.map { blob ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(
                    blob.alpha,
                    Color.red(blob.color),
                    Color.green(blob.color),
                    Color.blue(blob.color)
                )
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimations()
    }

    override fun onDetachedFromWindow() {
        stopAnimations()
        super.onDetachedFromWindow()
    }

    private fun startAnimations() {
        stopAnimations()
        blobs.forEachIndexed { i, blob ->
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = blob.speed
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    animProgress[i] = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            animators.add(anim)
        }
    }

    private fun stopAnimations() {
        animators.forEach { it.cancel() }
        animators.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        blobs.forEachIndexed { i, blob ->
            val path = blobPaths[i]
            path.reset()

            val t = animProgress[i] * (Math.PI * 2.0).toFloat() + blob.phaseOffset
            val r = blob.radius * density
            val driftX = cos(t * 0.7f) * r * 0.06f
            val driftY = sin(t * 0.6f) * r * 0.05f
            val cx = blob.cx * w + driftX
            val cy = blob.cy * h + driftY

            // 7-point irregular soft form
            val points = 7
            val angleStep = (Math.PI * 2.0 / points).toFloat()
            val p = mutableListOf<PointF>()

            for (k in 0 until points) {
                val angle = k * angleStep + t * 0.18f
                val wobble = r * (1f + 0.12f * sin(t * 2.1f + k * 1.3f))
                p.add(PointF(
                    cx + wobble * cos(angle),
                    cy + wobble * sin(angle)
                ))
            }

            path.moveTo(p[0].x, p[0].y)
            for (k in 0 until points) {
                val curr = p[k]
                val next = p[(k + 1) % points]
                val prev = p[(k + points - 1) % points]
                val next2 = p[(k + 2) % points]
                val cp1x = curr.x + (next.x - prev.x) * 0.24f
                val cp1y = curr.y + (next.y - prev.y) * 0.24f
                val cp2x = next.x - (next2.x - curr.x) * 0.24f
                val cp2y = next.y - (next2.y - curr.y) * 0.24f
                path.cubicTo(cp1x, cp1y, cp2x, cp2y, next.x, next.y)
            }
            path.close()

            canvas.drawPath(path, blobPaints[i])
        }
    }
}
