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
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.navigation.fragment.navArgs
import androidx.viewpager.widget.ViewPager
import org.avmedia.mirrormirror.BuildConfig
import org.avmedia.mirrormirror.R
import org.avmedia.mirrormirror.utils.BitmapExtractor
import org.avmedia.mirrormirror.utils.FileUploader
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

    private lateinit var mediaList: MutableList<File>

    /** Adapter class used to present a fragment containing one photo or video as a page */
    inner class MediaPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
        override fun getCount(): Int = mediaList.size
        override fun getItem(position: Int): Fragment = PhotoFragment.create(mediaList[position])
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
        mediaList = rootDirectory.listFiles { file ->
            EXTENSION_WHITELIST.contains(file.extension.toUpperCase())
        }.sorted().reversed().toMutableList()

        // rotate image in file to portrait mode.
        rotateImage(mediaList[0])

        // makeFrame(mediaList[0])
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(org.avmedia.mirrormirror.R.layout.fragment_gallery, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate the ViewPager and implement a cache of two media items
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

            // delete all left-over images.
            mediaList.forEach {
                it.delete()

                // Send relevant broadcast to notify other apps of deletion
                MediaScannerConnection.scanFile(
                        view.context, arrayOf(it.absolutePath), null, null)
            }

            // Notify our view pager
            mediaViewPager.adapter?.notifyDataSetChanged()

            fragmentManager?.popBackStack()
        }

        // Handle share button press
        view.findViewById<ImageButton>(R.id.share_button).setOnClickListener {
            // Make sure that we have a file to share
            mediaList.getOrNull(mediaViewPager.currentItem)?.let { mediaFile ->

                // Create a sharing intent
                val intent = Intent().apply {
                    // Infer media type from file extension
                    val mediaType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(mediaFile.extension)
                    // Get URI from our FileProvider implementation
                    val uri = FileProvider.getUriForFile(
                            view.context, BuildConfig.APPLICATION_ID + ".provider", mediaFile)
                    // Set the appropriate intent extra, type, action and flags
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = mediaType
                    action = Intent.ACTION_SEND
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                // Launch the intent letting the user choose which app to share with
                startActivity(Intent.createChooser(intent, getString(R.string.share_hint)))
            }
        }

        // INZ
        val textViewAge: TextView = view.findViewById(R.id.myImageViewText)
        textViewAge.text = "Estimating Your Age..."

        val file: File = mediaList[0]
        val successFunc: (msg: JSONObject) -> Unit = {
            println("Success...")

            val predictions: JSONArray? = it.get("predictions") as JSONArray
            if (predictions == null || predictions.length() == 0) {
                textViewAge.text = "Cannot estimate age!"
            } else {
                val prediction: JSONObject? = predictions[0] as JSONObject
                val age: Integer? = prediction?.get("age_estimation") as Integer
                textViewAge.text = "Estimaged Age: ${age}"

                makeFrame(mediaList[0])
            }
        }

        val failFunc: (msg: JSONObject) -> Unit = {
            println("Failed...")
            textViewAge.text = "Could not estimate age!"
        }

        val uploader = FileUploader(URL("http://max-facial-age-estimator.max.us-south.containers.appdomain.cloud"), successFunc, failFunc)

        uploader.upload(mediaList[0])
    }

    private fun makeFrame(file: File): Unit {
        val bmp = drawFaceRectanglesOnBitmap(BitmapExtractor.getBitmapFromFile(file, context as Context), Rect(100, 100, 200, 200))
        BitmapExtractor.setBitmapToFile(file, bmp, context as Context)
    }

    private fun drawFaceRectanglesOnBitmap(originalBitmap: Bitmap, faceRectangle: Rect): Bitmap {
        var bitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas: Canvas = Canvas(bitmap)
        val paint: Paint = Paint()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = Color.RED
        paint.strokeWidth = 10f
        canvas.drawRect(
                faceRectangle.left.toFloat(),
                faceRectangle.top.toFloat(),
                faceRectangle.left.toFloat() + faceRectangle.right.toFloat(),
                faceRectangle.top.toFloat() + faceRectangle.bottom.toFloat(),
                paint)
        return bitmap
    }

    //////////////////////
    // INZ image rotation:
    //////////////////////
    private fun rotateImage(file: File) {
        val bmp: Bitmap = pictureTurn(BitmapExtractor.getBitmapFromFile(file, context as Context), file.absolutePath)
        BitmapExtractor.setBitmapToFile(file, bmp, context as Context)
    }

    private fun pictureTurn(img: Bitmap, fileName: String): Bitmap {

        val exifInterface = ExifInterface(fileName)

        val exifR: Int = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
//        val orientation: Float =
//                when (exifR) {
//                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
//                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
//                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
//                    else -> 0f
//                }

        // the above does not work, and always gives 0 deg orientation, so we just harcode it 270 deg.
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
