/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.avmedia.ageestimator.fragments

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.Color.YELLOW
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.TextPaint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import org.avmedia.ageestimator.BuildConfig
import org.avmedia.ageestimator.R
import org.avmedia.ageestimator.utils.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONStringer
import java.io.File
import java.io.StringReader
import java.net.URL

val EXTENSION_WHITELIST = arrayOf("JPG")

/** Fragment used to present the user with a gallery of photos taken */
class GalleryFragment internal constructor() : Fragment() {

    /** AndroidX navigation arguments */
    private val args: GalleryFragmentArgs by navArgs()
    private lateinit var progressBarContainer: ProgressBarContainer
    private lateinit var imageFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true

        // Get root directory of media from navigation arguments
        val rootDirectory = File(args.rootDirectory)
        // val actionType = File(args.actionType)

        // Walk through all files in the root directory
        // We reverse the order of the list to present the last photos first
        imageFile = rootDirectory.listFiles { file ->
            EXTENSION_WHITELIST.contains(file.extension.toUpperCase())
        }.sorted().reversed().toMutableList()[0]

        // rotate image in file to portrait mode.
        rotateImage(imageFile)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(org.avmedia.ageestimator.R.layout.fragment_gallery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progressBar = view.findViewById<ProgressBar>(org.avmedia.ageestimator.R.id.progressBar)
        progressBarContainer = ProgressBarContainer(progressBar)

        // Make sure that the cutout "safe area" avoids the screen notch if any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use extension method to pad "inside" view containing UI using display cutout's bounds
            view.findViewById<ConstraintLayout>(R.id.cutout_safe_area).padWithDisplayCutout()
        }

        // Handle back button press
        view.findViewById<ImageButton>(org.avmedia.ageestimator.R.id.back_button).setOnClickListener {
            fragmentManager?.popBackStack()
        }

        // Handle share button press
        view.findViewById<ImageButton>(R.id.share_button).setOnClickListener {
            // Make sure that we have a file to share

            val intent = Intent()

            // Create a sharing intent
            intent.apply {
                // Infer media type from file extension
                val mediaType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(imageFile.extension)
                // Get URI from our FileProvider implementation
                val uri = FileProvider.getUriForFile(
                        view.context, BuildConfig.APPLICATION_ID + ".provider", imageFile)
                // Set the appropriate intent extra, type, action and flags
                putExtra(Intent.EXTRA_STREAM, uri)
                type = mediaType
                action = Intent.ACTION_SEND
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                drawQRCodeToFile (imageFile, "https://play.google.com/store/apps/details?id=org.avmedia.ageestimator")

                // Launch the intent letting the user choose which app to share with
                startActivity(Intent.createChooser(intent, getString(R.string.share_hint)))
            }
        }

        startUploader(view)
    }

    private fun drawQRCodeToFile (file: File, text: String): Unit {
        val qrGenrator: ZxingQrCodeGenerator = ZxingQrCodeGenerator()
        val qrBitmap: Bitmap = qrGenrator.generateQrCodeSync(text, 100, 100, Color.WHITE)

        val origBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(imageFile, context as Context)
        val bmpWithQR: Bitmap = drawQRCodeToBitmap (origBitmap, qrBitmap)
        BitmapExtractor.setBitmapToFile(imageFile, bmpWithQR, context as Context)
    }

    open fun drawQRCodeToBitmap(originalBitmap: Bitmap, qrBitmap: Bitmap): Bitmap {
        var bitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas: Canvas = Canvas(bitmap)
        val paint: Paint = Paint()
        paint.isAntiAlias = true

        val right = canvas.width-5
        val bottom = canvas.height-5
        val left = right - qrBitmap.width;
        val top = bottom - qrBitmap.height

        canvas.drawBitmap (qrBitmap,
                null,
                Rect (left, top, right, bottom),
                paint)

        // Draw text
        val scanMsg = "Get the app here:"
        val fm: Paint.FontMetrics = Paint.FontMetrics()
        val textPaint = TextPaint()
        textPaint.textSize = 20f
        textPaint.color = Color.RED
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = 2f

        val textWidth = (textPaint.measureText(scanMsg)).toDouble()
        val textX = (left - textWidth - 10).toFloat()
        val textY = (top + 4 + (qrBitmap.height/2)).toFloat()

        canvas.drawText(scanMsg, textX, textY, textPaint)

        textPaint.strokeWidth = 1f
        textPaint.color = Color.YELLOW
        canvas.drawText(scanMsg, textX, textY, textPaint)

        return bitmap
    }


    private fun startUploader(view: View?) {
        val textViewAge: TextView? = view?.findViewById(R.id.myImageViewText)

        // Display the captured image...
        val imgBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(imageFile, context as Context)
        var image: ImageView? = view?.findViewById<ImageView>(org.avmedia.ageestimator.R.id.image_view)
        image?.setImageBitmap(imgBitmap)

        val actionType = File(args.actionType)
        val  uploader:FileUploader

        if ("age" == actionType.toString()) {
            uploader = FileUploader(URL(getString(R.string.server_age_url)), getAgeDataObserver(), getProgressObserver())

        } else {
            uploader = FileUploader(URL(getString(R.string.server_mood_url)), getMoodDataObserver(), getProgressObserver())
        }

        uploader.upload(imageFile)
    }

    private fun getProgressObserver(): Observer<Int> {
        return progressBarContainer.getProgressObserver()
    }

    private fun getAgeDataObserver(): Observer<JSONObject> {
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

    private fun getMoodDataObserver(): Observer<JSONObject> {
        return object : Observer<JSONObject> {
            override fun onSubscribe(d: Disposable) {
            }

            override fun onNext(s: JSONObject) {
                successFuncMood (s)
            }

            override fun onError(e: Throwable) {
                failFunc (e.message)
            }

            override fun onComplete() {
            }
        }
    }
    val successFuncMood: (msg: JSONObject) -> Unit = {
        println("Mood Success...")
        val textViewAge: TextView? = view?.findViewById(R.id.myImageViewText)

        val predictions: JSONArray? = it.get("predictions") as JSONArray
        if (predictions == null || predictions.length() == 0) {
            textViewAge?.text = "Could not recognise face"
        } else {
            for (i in 0..(predictions.length() - 1)) {
                val prediction = predictions.getJSONObject(i)

                val emotions: JSONArray = prediction?.get("emotion_predictions") as JSONArray

                val detectionBox: JSONArray = prediction.get("detection_box") as JSONArray

                // this seems to be the order the data comes in
                val faceFrame: ImageBox = ImageBox(
                        detectionBox.get(1) as Double,
                        detectionBox.get(0) as Double,
                        detectionBox.get(3) as Double,
                        detectionBox.get(2) as Double)

                val emotionsList: List<Emotion> = makeEmotionList (emotions)
                makeMoodFrame(imageFile, faceFrame, emotionsList, view?.findViewById<ImageView>(org.avmedia.ageestimator.R.id.image_view))
            }
            // Hide the toolBar for error messages.
            val errorToolBar: Toolbar? = view?.findViewById<Toolbar>(org.avmedia.ageestimator.R.id.toolbar_message)
            errorToolBar?.visibility = View.GONE
        }

        progressBarContainer.hide()
    }

    private fun makeEmotionList (emotionsJsonArray: JSONArray): List<Emotion> {

        val gson = GsonBuilder().setPrettyPrinting().create()

        var emotions: List<Emotion> = gson.fromJson(emotionsJsonArray.toString(), object : TypeToken<List<Emotion>>() {}.type)
        emotions.forEach { println(it) }

        return emotions
    }

    val successFunc: (msg: JSONObject) -> Unit = {
        println("Success...")

        val textViewAge: TextView? = view?.findViewById(R.id.myImageViewText)

        val predictions: JSONArray? = it.get("predictions") as JSONArray
        if (predictions == null || predictions.length() == 0) {
            textViewAge?.text = "Could not recognise face"
        } else {
            for (i in 0..(predictions.length() - 1)) {
                val prediction = predictions.getJSONObject(i)

                val age: Integer = prediction?.get("age_estimation") as Integer

                val detectionBox: JSONArray = prediction.get("detection_box") as JSONArray

                // this seems to be the order the data comes in
                val faceFrame: ImageBox = ImageBox(
                        detectionBox.get(1) as Double,
                        detectionBox.get(0) as Double,
                        detectionBox.get(3) as Double,
                        detectionBox.get(2) as Double)

                makeAgeFrame(imageFile, faceFrame, age, view?.findViewById<ImageView>(org.avmedia.ageestimator.R.id.image_view))
            }
            // Hide the toolBar for error messages.
            val errorToolBar: Toolbar? = view?.findViewById<Toolbar>(org.avmedia.ageestimator.R.id.toolbar_message)
            errorToolBar?.visibility = View.GONE
        }

        progressBarContainer.hide()
    }

    val failFunc: (msg: String?) -> Unit = {
        val textViewAge: TextView? = view?.findViewById(R.id.myImageViewText)
        textViewAge?.text = it
        progressBarContainer.hide()
    }

    private fun makeAgeFrame(file: File, faceFrame: ImageBox, age: Integer, imageView: ImageView?) {

        // Draw the frame with the age.
        val origBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(file, context as Context)
        val bmpWithFrame = drawFaceRectanglesOnBitmap(origBitmap, faceFrame)
        val bmpWithAge = drawAgeOnBitmap(bmpWithFrame, faceFrame, age)
        BitmapExtractor.setBitmapToFile(file, bmpWithAge, context as Context)

        // update the view
        imageView?.setImageBitmap(bmpWithAge)
    }

    private fun makeMoodFrame(file: File, faceFrame: ImageBox, emotions: List<Emotion>, imageView: ImageView?) {

        // Draw the frame with the age.
        val origBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(file, context as Context)
        val bmpWithFrame = drawFaceRectanglesOnBitmap(origBitmap, faceFrame)
        val bmpWithEmotions = drawEmotionsOnBitmap(bmpWithFrame, faceFrame, emotions)
        BitmapExtractor.setBitmapToFile(file, bmpWithEmotions, context as Context)

        // update the view
        imageView?.setImageBitmap(bmpWithEmotions)
    }

    open fun drawFaceRectanglesOnBitmap(originalBitmap: Bitmap, faceFrame: ImageBox): Bitmap {
        var bitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas: Canvas = Canvas(bitmap)
        val paint: Paint = Paint()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = YELLOW
        paint.strokeWidth = 1f
        canvas.drawRect(
                faceFrame.scale(canvas.width, canvas.height).toRect(),
                paint)

        return bitmap
    }

    private fun drawEmotionsOnBitmap(originalBitmap: Bitmap, faceFrame: ImageBox, emotions: List<Emotion>): Bitmap {
        var bitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas: Canvas = Canvas(bitmap)

        val x = (faceFrame.x1 * canvas.width).toFloat()
        val y = (faceFrame.y1 * canvas.height).toFloat()

        // Draw text
        val fm: Paint.FontMetrics = Paint.FontMetrics()
        val textPaint = TextPaint()
        textPaint.textSize = 20f

        textPaint.color = Color.YELLOW
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = 2f

        var yOffset: Int = 5;
        for (emotion in emotions) {
            val text = "${emotion.label}: ${(emotion.probability*100).toInt()}%"
            canvas.drawText(text, x, y+yOffset, textPaint)
            val bounds:Rect = Rect();
            textPaint.getTextBounds(text, 0, text.length, bounds);
            val height = bounds.height();
            yOffset += height + 4
        }

        return bitmap
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

        textPaint.color = Color.RED
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = 6f

        val textWidth = (textPaint.measureText(ageStr)).toDouble()
        val frameCenter = (faceFrame.x1+faceFrame.x2) * canvas.width / 2
        val xCenter: Float = (frameCenter - textWidth/2).toFloat()

        canvas.drawText(ageStr, xCenter, y+4, textPaint)

        textPaint.color = Color.YELLOW
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = 2f

        canvas.drawText(ageStr, xCenter, y+4, textPaint)

        return bitmap
    }

    private fun rotateImage(file: File) {
        val bmp: Bitmap = pictureTurn(BitmapExtractor.getBitmapFromFile(file, context as Context), file.absolutePath)
        BitmapExtractor.setBitmapToFile(file, bmp, context as Context)
    }

    // Rotate the image because the camera takes it in landscape mode.
    private fun pictureTurn(img: Bitmap, fileName: String): Bitmap {

        val exifInterface = ExifInterface(fileName)

        val exifR: Int = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        val orientation = 270f

        val mat: Matrix? = Matrix()
        mat?.postRotate(orientation)

        // mirror the image
        mat?.postScale(-1f, 1f, img.width / 2f, img.height / 2f)

        return scaleTo(Bitmap.createBitmap(img, 0, 0, img.width,
                img.height, mat, false), 720)
    }

    private fun scaleTo(image: Bitmap, maxSize: Int): Bitmap {
        var width: Float = image.width.toFloat()
        var height: Float = image.height.toFloat()

        val bitmapRatio: Float = width / height
        if (bitmapRatio > 1) {
            width = maxSize.toFloat()
            height = width / bitmapRatio
        } else {
            height = maxSize.toFloat()
            width = (height * bitmapRatio)
        }

        return Bitmap.createScaledBitmap(image, width.toInt(), height.toInt(), true)
    }

    // code related to flashing frame
    private enum class FlashState { ON, OFF }

    private class FlashPatternCycle(_state: FlashState, _duration: Long) {

        var state: FlashState
        var duration: Long

        init {
            this.state = _state
            this.duration = _duration
        }
    }

    private inner class FrameFlasher {

        open fun flashFrame(file: File, context: Context, faceFrame: ImageBox, flashPattern: List<FlashPatternCycle>, image: ImageView?): Bitmap {

            val origBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(file, context)
            var retBitmap: Bitmap = origBitmap

            for (flashCycle in flashPattern) {
                if (flashCycle.state == FlashState.ON) {
                    Handler().postDelayed({
                        val origBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(file, context)
                        retBitmap = drawFaceRectanglesOnBitmap(origBitmap, faceFrame)
                        val bmp: Bitmap = BitmapExtractor.setBitmapToFile(file, retBitmap, context)
                        image?.setImageBitmap(bmp)
                    }, flashCycle.duration)


                } else {
                    Handler().postDelayed({
                        BitmapExtractor.setBitmapToFile(file, origBitmap, context)
                        image?.setImageBitmap(retBitmap)
                    }, flashCycle.duration)
                }
            }
            return retBitmap
        }
    }

    private data class Emotion (val labelId: Int, val label: String, val probability:Float) {

    }
}
