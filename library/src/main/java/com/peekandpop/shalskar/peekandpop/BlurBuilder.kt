package com.peekandpop.shalskar.peekandpop

import android.content.Context
import android.graphics.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import kotlin.math.roundToInt

object BlurBuilder {
    private const val BITMAP_SCALE = 0.2f
    private const val BLUR_RADIUS = 6.0f

    fun blur(v: View): Bitmap {
        return blur(v.context, getScreenshot(v))
    }

    fun blur(ctx: Context, image: Bitmap): Bitmap {
        val width = (image.width * BITMAP_SCALE).roundToInt()
        val height = (image.height * BITMAP_SCALE).roundToInt()
        val inputBitmap = Bitmap.createScaledBitmap(image, width, height, false)
        val outputBitmap = Bitmap.createBitmap(inputBitmap)
        val rs = RenderScript.create(ctx)
        val theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val tmpIn = Allocation.createFromBitmap(rs, inputBitmap)
        val tmpOut = Allocation.createFromBitmap(rs, outputBitmap)
        theIntrinsic.setRadius(BLUR_RADIUS)
        theIntrinsic.setInput(tmpIn)
        theIntrinsic.forEach(tmpOut)
        tmpOut.copyTo(outputBitmap)
        return darkenBitmap(outputBitmap)
    }

    private fun getScreenshot(v: View): Bitmap {
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        v.draw(c)
        return b
    }

    private fun darkenBitmap(bitmap: Bitmap): Bitmap {
        val canvas = Canvas(bitmap)
        val paint = Paint(Color.BLACK)
        val filter: ColorFilter = LightingColorFilter(0xAAAAAA, 0x000000)
        paint.colorFilter = filter
        canvas.drawBitmap(bitmap, Matrix(), paint)
        return bitmap
    }
}