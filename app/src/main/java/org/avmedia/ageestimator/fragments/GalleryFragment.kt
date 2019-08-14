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

        // Display the captured image...
        val imgBitmap: Bitmap = BitmapExtractor.getBitmapFromFile(imageFile, context as Context)
        var image: ImageView? = view?.findViewById<ImageView>(org.avmedia.ageestimator.R.id.image_view)
        image?.setImageBitmap(imgBitmap)

        val actionType = File(args.actionType)
        val  uploader:FileUploader

        if ("age" == actionType.toString()) {
            val ageHandler = AgeDisplayHandler(view, imageFile, context as Context)
            uploader = FileUploader(URL(getString(R.string.server_age_url)), ageHandler.getDataObserver(), progressBarContainer.getProgressObserver())

        } else {
            val moodHandler = MoodDisplayHandler(view, imageFile, context as Context)
            uploader = FileUploader(URL(getString(R.string.server_mood_url)), moodHandler.getDataObserver(), progressBarContainer.getProgressObserver())
        }

        uploader.upload(imageFile)
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
}
