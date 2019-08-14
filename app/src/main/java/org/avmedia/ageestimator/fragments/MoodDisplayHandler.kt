package org.avmedia.ageestimator.fragments

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import org.avmedia.ageestimator.R
import org.avmedia.ageestimator.utils.BitmapExtractor
import org.avmedia.ageestimator.utils.ImageBox
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MoodDisplayHandler (val view: View?, val imageFile: File, val context: Context) : DisplayHandler () {
    override fun getDataObserver(): Observer<JSONObject> {
        return object : Observer<JSONObject> {
            override fun onSubscribe(d: Disposable) {
            }

            override fun onNext(s: JSONObject) {
                successFunc (s)
            }

            override fun onError(e: Throwable) {
                failFunc (e.message)
            }

            override fun onComplete() {
            }
        }
    }

    override val failFunc: (msg: String?) -> Unit = {
        val textViewAge: TextView? = view?.findViewById(R.id.myImageViewText)
        textViewAge?.text = it
    }

    override val successFunc: (msg: JSONObject) -> Unit = {
        println("Mood Success...")
        val textViewAge: TextView? = view?.findViewById(R.id.myImageViewText)

        val predictions: JSONArray? = it.get("predictions") as JSONArray
        if (predictions == null || predictions.length() == 0) {
            textViewAge?.text = "Could not recognise face"
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

    override fun makeFrame(file: File, faceFrame: ImageBox, prediction:JSONObject, imageView: ImageView?) {

        val emotionsJson: JSONArray = prediction?.get("emotion_predictions") as JSONArray
        val emotions: List<Emotion> = makeEmotionList (emotionsJson)

        // Draw the frame with the age.
        val origBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(file, context as Context)
        val bmpWithFrame = drawFaceRectanglesOnBitmap(origBitmap, faceFrame)
        val bmpWithEmotions = drawEmotionsOnBitmap(bmpWithFrame, faceFrame, emotions)
        BitmapExtractor.setBitmapToFile(file, bmpWithEmotions, context as Context)

        // update the view
        imageView?.setImageBitmap(bmpWithEmotions)
    }

    private fun makeEmotionList (emotionsJsonArray: JSONArray): List<Emotion> {

        val gson = GsonBuilder().setPrettyPrinting().create()
        var emotions: List<Emotion> = gson.fromJson(emotionsJsonArray.toString(), object : TypeToken<List<Emotion>>() {}.type)
        return emotions
    }

    private fun drawEmotionsOnBitmap(originalBitmap: Bitmap, faceFrame: ImageBox, emotions: List<Emotion>): Bitmap {
        var bitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas: Canvas = Canvas(bitmap)

        val x = (faceFrame.x1 * canvas.width).toFloat()
        val y = (faceFrame.y1 * canvas.height).toFloat()
        val frameWidth = ((faceFrame.x2 - faceFrame.x1)*canvas.width).toFloat()

        // Draw text
        val fm: Paint.FontMetrics = Paint.FontMetrics()
        val textPaint = TextPaint()
        textPaint.textSize = 20f

        var yOffset: Int = 20
        var xCenter = 0f
        for (emotion in emotions) {
            val text = "${emotion.label}: ${"%.2f".format(emotion.probability*100)}%"

            val textWidth = textPaint.measureText(text)
            xCenter = ((frameWidth - textWidth)/2).toFloat()

            drawText(text, x + xCenter, y+yOffset, canvas, textSize = 20f, strokeWidth = 1f)
            val bounds: Rect = Rect()
            textPaint.getTextBounds(text, 0, text.length, bounds)
            val height = bounds.height()
            yOffset += height+4
        }

        return bitmap
    }

    private data class Emotion (val labelId: Int, val label: String, val probability:Float) {}

}