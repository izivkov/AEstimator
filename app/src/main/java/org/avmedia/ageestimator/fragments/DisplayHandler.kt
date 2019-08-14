package org.avmedia.ageestimator.fragments

import android.graphics.*
import android.text.TextPaint
import android.widget.ImageView
import io.reactivex.Observer
import org.avmedia.ageestimator.utils.DisplayHelper
import org.json.JSONObject
import java.io.File

import org.avmedia.ageestimator.utils.ImageBox

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

        DisplayHelper.drawText(text, x, y, canvas, textSize, strokeWidth, color)
    }
}