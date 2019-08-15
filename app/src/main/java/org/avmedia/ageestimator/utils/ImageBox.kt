/*
Author: Ivo Zivkov
 */

package org.avmedia.ageestimator.utils

import android.graphics.Rect

data class ImageBox(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
    open fun scale(factorW: Int, factorH: Int): ImageBox {
        return ImageBox(x1 * factorW, y1 * factorH, x2 * factorW, y2 * factorH)
    }

    open fun toRect(): Rect {
        return Rect(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
    }
}

