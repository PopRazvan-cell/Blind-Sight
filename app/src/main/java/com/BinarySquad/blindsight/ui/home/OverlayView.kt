package com.BinarySquad.blindsight.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var results: List<HomeFragment.DetectionResult> = emptyList()
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        textSize = 40f
    }

    fun setResults(newResults: List<HomeFragment.DetectionResult>) {
        results = newResults
        Log.d("ObjectDetection", "OverlayView received ${results.size} detections: $results")
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (result in results) {
            val scaledBox = scaleBoundingBox(result.boundingBox)
            Log.d("ObjectDetection", "Drawing detection: ${result.label}, confidence: ${result.confidence}, box: $scaledBox")
            canvas.drawRect(scaledBox, paint)
            canvas.drawText(
                "${result.label} (${String.format("%.2f", result.confidence)})",
                scaledBox.left,
                scaledBox.top - 10,
                paint
            )
        }
    }

    private fun scaleBoundingBox(box: RectF): RectF {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val modelSize = 48f // Model input size
        val scaledBox = RectF(
            box.left * viewWidth / modelSize,
            box.top * viewHeight / modelSize,
            box.right * viewWidth / modelSize,
            box.bottom * viewHeight / modelSize
        )
        return scaledBox
    }
}