package com.datakaaval.app

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object IconReader {

    fun read(ctx: Context, ids: List<String>): String {
        val pm = ctx.packageManager
        val out = JSONObject()
        for (id in ids) {
            try {
                val pkg = if (id == "system") "android" else id
                out.put(id, png(pm.getApplicationIcon(pkg), 72))
            } catch (e: Exception) {
                // no icon for this id (removed app, etc.); the UI shows a letter tile
            }
        }
        return out.toString()
    }

    private fun png(d: Drawable, px: Int): String {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (d is AdaptiveIconDrawable) {
            val pad = (px * 0.25f).toInt()
            d.setBounds(-pad, -pad, px + pad, px + pad)
        } else {
            d.setBounds(0, 0, px, px)
        }
        d.draw(c)
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }
}
