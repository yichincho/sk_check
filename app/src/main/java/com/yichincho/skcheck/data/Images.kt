package com.yichincho.skcheck.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object Images {
    /** Copies a picked or shared image into the app's own storage, so it stays even if the original is deleted. */
    fun copyIn(context: Context, uri: Uri, id: String): File {
        val out = Store.shotFile("$id.img")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "打不開這張圖" }
            out.outputStream().use { input.copyTo(it) }
        }
        return out
    }

    /** Decodes [file] so its longer side is at most about [maxSide] pixels. */
    fun decode(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * A JPEG of the screenshot as base64, small enough to send inline (NVIDIA rejects inline images much
     * over ~180 KB): shrinks quality first, then size, until it fits.
     */
    fun toJpegBase64(file: File, limitChars: Int = 170_000): String {
        var bmp = decode(file, 2000) ?: throw NetException("這張圖讀不出來")
        val longSide = maxOf(bmp.width, bmp.height)
        if (longSide > 1600) bmp = scale(bmp, 1600.0 / longSide)
        while (true) {
            for (q in listOf(85, 75, 65, 55)) {
                val b64 = encode(bmp, q)
                if (b64.length <= limitChars) return b64
            }
            if (maxOf(bmp.width, bmp.height) < 500) return encode(bmp, 50)
            bmp = scale(bmp, 0.75)
        }
    }

    private fun scale(b: Bitmap, f: Double): Bitmap =
        Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)

    private fun encode(b: Bitmap, quality: Int): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
