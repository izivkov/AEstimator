/*
Author: Ivo Zivkov
 */

package org.avmedia.ageestimator.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.ColorInt
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

interface QrCodeGenerator {
    fun generateQrCode(contents: String, width: Int = 512, height: Int = 512, @ColorInt backgroundColor: Int = Color.WHITE): Single<Bitmap>
    fun generateQrCodeSync(contents: String, width: Int, height: Int, @ColorInt backgroundColor: Int): Bitmap
}

class ZxingQrCodeGenerator : QrCodeGenerator {
    override fun generateQrCode(contents: String, width: Int, height: Int, @ColorInt backgroundColor: Int): Single<Bitmap> =
            Single.fromCallable {
                generateQrCodeSync(contents, width, height, backgroundColor)
            }
                    .subscribeOn(Schedulers.computation())

    override fun generateQrCodeSync(contents: String, width: Int, height: Int, @ColorInt backgroundColor: Int): Bitmap {
        val writer = QRCodeWriter()
        try {
            val bitMatrix = writer.encode(contents, BarcodeFormat.QR_CODE, width, height)
            val bmp = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else backgroundColor)
                }
            }
            return bmp
        } catch (e: Throwable) {
            throw e
        }
    }
}
