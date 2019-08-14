package org.avmedia.ageestimator.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint

class DisplayHelper {

    companion object {
        fun drawText(text: String, x: Float, y: Float, canvas: Canvas, textSize: Float = 20f, strokeWidth: Float = 2f, color: Int = Color.YELLOW): Unit {

            val textPaint = TextPaint()

            textPaint.textSize = textSize
            textPaint.color = color
            textPaint.style = Paint.Style.FILL_AND_STROKE
            textPaint.strokeWidth = strokeWidth

            val bkgPaint = Paint()
            /* Hex opacity values:

                100% — FF
                95% — F2
                90% — E6
                85% — D9
                80% — CC
                75% — BF
                70% — B3
                65% — A6
                60% — 99
                55% — 8C
                50% — 80
                45% — 73
                40% — 66
                35% — 59
                30% — 4D
                25% — 40
                20% — 33
                15% — 26
                10% — 1A
                5% — 0D
                0% — 00
        */

            bkgPaint.color = Color.parseColor("#66000000") // 40% transparent
            bkgPaint.style = Paint.Style.FILL

            val fm = textPaint.fontMetrics

            val height = (fm.bottom - fm.top) / 2
            val width = textPaint.measureText(text)
            val margin = 4
            var rounding: Float = (height * .2).toFloat()

            canvas.drawRoundRect(RectF((x - margin), (y - height - margin), (x + margin + width), (y + margin)), rounding, rounding, bkgPaint)

            // draw the text
            canvas.drawText(text, x, y, textPaint)
        }
    }
}