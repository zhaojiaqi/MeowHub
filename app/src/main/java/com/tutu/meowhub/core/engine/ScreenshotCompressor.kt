package com.tutu.meowhub.core.engine

import android.graphics.*
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 截图压缩工具 —— 与 PHP 端 toGrayscaleDataURL 完全一致：
 * 1) 缩放到 50%
 * 2) 灰度化 (R*0.299 + G*0.587 + B*0.114)
 * 3) JPEG quality=20
 */
object ScreenshotCompressor {

    private val grayscaleColorFilter = ColorMatrixColorFilter(
        ColorMatrix().apply { setSaturation(0f) }
    )

    fun compress(rawBase64: String): String {
        val bytes = Base64.decode(rawBase64, Base64.DEFAULT)
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return rawBase64

        val tw = original.width / 2
        val th = original.height / 2
        if (tw <= 0 || th <= 0) return rawBase64

        val scaled = Bitmap.createScaledBitmap(original, tw, th, true)

        val grayscale = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = grayscaleColorFilter
        }
        canvas.drawBitmap(scaled, 0f, 0f, paint)

        val stream = ByteArrayOutputStream()
        grayscale.compress(Bitmap.CompressFormat.JPEG, 20, stream)

        original.recycle()
        if (scaled !== original) scaled.recycle()
        grayscale.recycle()

        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
