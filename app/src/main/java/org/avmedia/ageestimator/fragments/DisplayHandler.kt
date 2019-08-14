package org.avmedia.ageestimator.fragments

import android.graphics.*
import android.text.TextPaint
import android.widget.ImageView
import io.reactivex.Observer
import org.avmedia.ageestimator.utils.ImageBox
import org.json.JSONObject
import java.io.File

abstract class DisplayHandler {

    abstract fun getDataObserver(): Observer<JSONObject>
    abstract val successFunc: (msg: JSONObject) -> Unit
    abstract val failFunc: (msg: String?) -> Unit
    abstract fun makeFrame(file: File, faceFrame: ImageBox, json: JSONObject, imageView: ImageView?)

    fun drawFaceRectanglesOnBitmap(originalBitmap: Bitmap, faceFrame: ImageBox): Bitmap {
        var bitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas: Canvas = Canvas(bitmap)
        val paint: Paint = Paint()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = Color.YELLOW
        paint.strokeWidth = 1f
        canvas.drawRect(
                faceFrame.scale(canvas.width, canvas.height).toRect(),
                paint)

        return bitmap
    }

    fun drawText(text: String, x: Float, y: Float, canvas: Canvas, textSize: Float = 20f, strokeWidth: Float = 2f, color: Int = Color.YELLOW): Unit {

        // val fm: Paint.FontMetrics = Paint.FontMetrics()
        val textPaint = TextPaint()

        textPaint.textSize = textSize
        textPaint.color = color
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = strokeWidth
        textPaint.isAntiAlias = true

        val bgdPaint = Paint()
        bgdPaint.color = Color.BLUE
        bgdPaint.style = Paint.Style.FILL
        bgdPaint.isAntiAlias = true
        val bounds: Rect = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)

        // draw background
        canvas.drawRect(Rect (x.toInt(), (y-bounds.height()).toInt(), (x+bounds.width()).toInt(), (y).toInt()), bgdPaint)
        // canvas.drawRect(Rect (bounds.left, bounds.top, bounds.right, bounds.bottom), bgdPaint)

        // draw the text
        canvas.drawText(text, x, y, textPaint)
    }
}