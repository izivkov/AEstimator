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

package org.avmedia.mirrormirror.fragments

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.Color.RED
import android.graphics.Color.YELLOW
import android.os.Build
import android.os.Bundle
import android.provider.Contacts
import android.text.TextPaint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.navigation.fragment.navArgs
import androidx.viewpager.widget.ViewPager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.avmedia.mirrormirror.BuildConfig
import org.avmedia.mirrormirror.MainActivity
import org.avmedia.mirrormirror.R
import org.avmedia.mirrormirror.utils.BitmapExtractor
import org.avmedia.mirrormirror.utils.FileUploader
import org.avmedia.mirrormirror.utils.ProgressBarContainer
import org.avmedia.mirrormirror.utils.padWithDisplayCutout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

val EXTENSION_WHITELIST = arrayOf("JPG")

/** Fragment used to present the user with a gallery of photos taken */
class GalleryFragment internal constructor() : Fragment() {

    /** AndroidX navigation arguments */
    private val args: GalleryFragmentArgs by navArgs()
    private lateinit var progressBarContainer: ProgressBarContainer
    private lateinit var imageFile: File

    /** Adapter class used to present a fragment containing one photo or video as a page */
    inner class MediaPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
        override fun getCount(): Int = 1
        override fun getItem(position: Int): Fragment = PhotoFragment.create(imageFile)
        override fun getItemPosition(obj: Any): Int = POSITION_NONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true

        // Get root directory of media from navigation arguments
        val rootDirectory = File(args.rootDirectory)

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
    ): View? = inflater.inflate(org.avmedia.mirrormirror.R.layout.fragment_gallery, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progressBar = view.findViewById<ProgressBar>(org.avmedia.mirrormirror.R.id.progressBar)
        progressBarContainer = ProgressBarContainer(progressBar)
        progressBarContainer.show()

        val mediaViewPager = view.findViewById<ViewPager>(org.avmedia.mirrormirror.R.id.photo_view_pager).apply {
            offscreenPageLimit = 0 // INZ was 2
            adapter = MediaPagerAdapter(childFragmentManager)
        }

        // Make sure that the cutout "safe area" avoids the screen notch if any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use extension method to pad "inside" view containing UI using display cutout's bounds
            view.findViewById<ConstraintLayout>(R.id.cutout_safe_area).padWithDisplayCutout()
        }

        // Handle back button press
        view.findViewById<ImageButton>(org.avmedia.mirrormirror.R.id.back_button).setOnClickListener {
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

                // Launch the intent letting the user choose which app to share with
                startActivity(Intent.createChooser(intent, getString(R.string.share_hint)))
            }
        }

        val textViewAge: TextView = view.findViewById(R.id.myImageViewText)

        val file: File = imageFile
        val successFunc: (msg: JSONObject) -> Unit = {
            println("Success...")

            val predictions: JSONArray? = it.get("predictions") as JSONArray
            if (predictions == null || predictions.length() == 0) {
                textViewAge.text = "Could not recognise face"
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

                    makeFrame(imageFile, faceFrame, age)
                }
            }
            progressBarContainer.hide()
        }

        val failFunc: (msg: JSONObject) -> Unit = {
            println("Failed...")
            textViewAge.text = it.getString("msg")
            progressBarContainer.hide()
        }

        val uploader = FileUploader(URL("http://max-facial-age-estimator.max.us-south.containers.appdomain.cloud"), successFunc, failFunc)

        uploader.setProgressListener (progressBarContainer.showProgress)
        uploader.upload(imageFile)
    }

    private fun makeFrame(file: File, faceFrame: ImageBox, age: Integer): Unit {
        val imgBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(file, context as Context)
        val bmpWithFrame = drawFaceRectanglesOnBitmap(imgBitmap, faceFrame)
        val bmp = drawAgeOnBitmap(bmpWithFrame, faceFrame, age)
        BitmapExtractor.setBitmapToFile(file, bmp, context as Context)
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

    private fun drawAgeOnBitmap(originalBitmap: Bitmap, faceFrame: ImageBox, age: Integer): Bitmap {
        var bitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas: Canvas = Canvas(bitmap)
        val ageStr = age.toString()

        // Draw text
        val fm: Paint.FontMetrics = Paint.FontMetrics()
        val textPaint = TextPaint()
        textPaint.color = RED
        textPaint.textSize = 24f
        textPaint.strokeWidth = 1f

        textPaint.getFontMetrics(fm)
        val margin: Int = 0

        val bkPain = Paint()
        bkPain.color = Color.YELLOW
        val x = (faceFrame.x1 * canvas.width).toFloat()
        val y = (faceFrame.y1 * canvas.height).toFloat()
        canvas.drawRect((x - margin), y + fm.top - margin,
                x + textPaint.measureText(ageStr) + margin, y + fm.bottom
                + margin, bkPain)

        canvas.drawText(ageStr, x, y, textPaint)

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

    data class ImageBox(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
        open fun scale(factorW: Int, factorH: Int): ImageBox {
            return ImageBox(x1 * factorW, y1 * factorH, x2 * factorW, y2 * factorH)
        }

        open fun toRect(): Rect {
            return Rect(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
        }
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

        open fun flashFrame(file: File, context: Context, faceFrame: ImageBox, flashPattern: List<FlashPatternCycle>): Bitmap {

            val origBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(file, context)
            var retBitmap: Bitmap = origBitmap

            for (flashCycle in flashPattern) {
                if (flashCycle.state == FlashState.ON) {
                    val origBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(file, context)
                    retBitmap = drawFaceRectanglesOnBitmap(origBitmap, faceFrame)
                    BitmapExtractor.setBitmapToFile(file, retBitmap, context)
                    //delay(flashCycle.duration)

                } else {
                    BitmapExtractor.setBitmapToFile(file, origBitmap, context)
                    //delay(flashCycle.duration)
                }
            }
            return retBitmap
        }
    }
}
