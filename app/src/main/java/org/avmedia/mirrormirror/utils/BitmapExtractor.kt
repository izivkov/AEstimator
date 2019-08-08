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
            val bmp: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, file.toUri())
            return bmp
        }

        @JvmStatic
        fun setBitmapToFile(file : File, bitmap: Bitmap, context: Context): Bitmap {
            val os: OutputStream = context!!.contentResolver.openOutputStream(file.toUri())
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
            os.flush()
            return bitmap
        }
    }
}