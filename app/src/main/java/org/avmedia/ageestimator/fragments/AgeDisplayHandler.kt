/*
Author: Ivo Zivkov
 */

package org.avmedia.ageestimator.fragments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import org.avmedia.ageestimator.R
import org.avmedia.ageestimator.utils.BitmapExtractor
import org.avmedia.ageestimator.utils.ImageBox
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AgeDisplayHandler (val view: View?, val imageFile: File, val context: Context) : DisplayHandler () {

    override val successFunc: (msg: JSONObject) -> Unit = {
        val predictions: JSONArray? = it.get("predictions") as JSONArray
        if (predictions == null || predictions.length() == 0) {
            getDataObserver().onError(Throwable("Could not recognise face"))
        } else {
            for (i in 0..(predictions.length() - 1)) {
                val prediction = predictions.getJSONObject(i)

                val detectionBox: JSONArray = prediction.get("detection_box") as JSONArray

                // this seems to be the order the data comes in
                val faceFrame: ImageBox = ImageBox(
                        detectionBox.get(1) as Double,
                        detectionBox.get(0) as Double,
                        detectionBox.get(3) as Double,
                        detectionBox.get(2) as Double)

                makeFrame(imageFile, faceFrame, prediction, view?.findViewById<ImageView>(org.avmedia.ageestimator.R.id.image_view))
            }
            // Hide the toolBar for error messages.
            val errorToolBar: Toolbar? = view?.findViewById<Toolbar>(org.avmedia.ageestimator.R.id.toolbar_message)
            errorToolBar?.visibility = View.GONE
        }
    }

    override val failFunc: (msg: String?) -> Unit = {
        val textViewAge: TextView? = view?.findViewById(R.id.myImageViewText)
        textViewAge?.text = it
    }

    override fun makeFrame(file: File, faceFrame: ImageBox, prediction: JSONObject, imageView: ImageView?) {

        val age: Integer = prediction?.get("age_estimation") as Integer

        // Draw the frame with the age.
        val origBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(file, context)
        val bmpWithFrame = drawFaceRectanglesOnBitmap(origBitmap, faceFrame)
        val bmpWithAge = drawAgeOnBitmap(bmpWithFrame, faceFrame, age)
        BitmapExtractor.setBitmapToFile(file, bmpWithAge, context)

        // update the view
        imageView?.setImageBitmap(bmpWithAge)
    }

    private fun drawAgeOnBitmap(originalBitmap: Bitmap, faceFrame: ImageBox, age: Integer): Bitmap {
        var bitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas: Canvas = Canvas(bitmap)
        val ageStr = age.toString()

        val x = (faceFrame.x1 * canvas.width).toFloat()
        val y = (faceFrame.y1 * canvas.height).toFloat()

        // Draw text
        val fm: Paint.FontMetrics = Paint.FontMetrics()
        val textPaint = TextPaint()
        textPaint.textSize = 32f

        val textWidth = (textPaint.measureText(ageStr)).toDouble()
        val frameCenter = (faceFrame.x1+faceFrame.x2) * canvas.width / 2
        val xCenter: Float = (frameCenter - textWidth/2).toFloat()

        drawText(ageStr, xCenter, y+4, canvas, textSize = 32f)

        return bitmap
    }
}