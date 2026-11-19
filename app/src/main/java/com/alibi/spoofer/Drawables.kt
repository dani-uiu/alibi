package com.alibi.spoofer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt

fun createIntermediateRouteDrawable(context: Context): Drawable {
    val size = 72
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#40000000".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 3f, 24f, shadowPaint)

    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#33F59E0B".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 28f, glowPaint)

    val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#F59E0B".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 20f, mainPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    canvas.drawCircle(cx, cy, 20f, borderPaint)

    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 8f, corePaint)

    return bitmap.toDrawable(context.resources)
}

fun createRealLocationDrawable(context: Context): Drawable {
    val size = 80
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#40000000".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 3f, 24f, shadowPaint)

    // Outer Cyan glowing aura
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#3306B6D4".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 32f, glowPaint)

    // Vibrant Blue/Cyan main body
    val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#0284C7".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 22f, mainPaint)

    // Crisp white border ring
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(cx, cy, 22f, borderPaint)

    // White Core Center
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 8f, corePaint)

    // Inner Dark Blue Dot
    val innerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#0369A1".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 4f, innerDot)

    return bitmap.toDrawable(context.resources)
}
// Custom Marker Drawables using Canvas & Paint

fun createRouteStartDrawable(context: Context): Drawable {
    val size = 72
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#40000000".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 3f, 24f, shadowPaint)

    // Outer Emerald Green glowing aura
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#3310B981".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 28f, glowPaint)

    // Emerald Green main body
    val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#10B981".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 20f, mainPaint)

    // Crisp white border ring
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    canvas.drawCircle(cx, cy, 20f, borderPaint)

    // Crisp white core
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 8f, corePaint)

    return bitmap.toDrawable(context.resources)
}

fun createRouteDestinationDrawable(context: Context): Drawable {
    val size = 72
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#40000000".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 3f, 24f, shadowPaint)

    // Outer Ruby Red glowing aura
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#33EF4444".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 28f, glowPaint)

    // Ruby Red main body
    val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#EF4444".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 20f, mainPaint)

    // Crisp white border ring
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    canvas.drawCircle(cx, cy, 20f, borderPaint)

    // Crisp white core
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 8f, corePaint)

    return bitmap.toDrawable(context.resources)
}

fun createSingleTargetDrawable(context: Context): Drawable {
    val size = 72
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#30000000".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 3f, 24f, shadowPaint)

    // Outer Indigo/Violet ring
    val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#8B5CF6".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(cx, cy, 24f, outerRingPaint)

    // Fill Circle
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#7C3AED".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 16f, fillPaint)

    // Crisp White Core Dot
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 6f, corePaint)

    return bitmap.toDrawable(context.resources)
}

fun createActiveMockLocationDrawable(context: Context): Drawable {
    val size = 80
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Semi-transparent outer halo
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#330091EA".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 34f, haloPaint)

    // Middle pulse ring
    val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#660091EA".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 22f, pulsePaint)

    // Electric Blue main core
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#0091EA".toColorInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 14f, corePaint)

    // Crisp white border ring
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(cx, cy, 14f, borderPaint)

    // White center pulse point
    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 5f, centerPaint)

    return bitmap.toDrawable(context.resources)
}
