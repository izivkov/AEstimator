package org.avmedia.mirrormirror.utils

import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File
import java.io.OutputStream

class BitmapExtractor {
    companion object {
        @JvmStatic
        fun getBitmapFromFile(file : File, context: Context): Bitmap {
            var contentResolver = context!!.contentResolver
            return MediaStore.Images.Media.getBitmap(contentResolver, file.toUri())
        }

        @JvmStatic
        fun setBitmapToFile(file : File, bitmap: Bitmap, context: Context): Unit {
            val os: OutputStream = context!!.contentResolver.openOutputStream(file.toUri())
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
            os.flush()
        }
    }
}