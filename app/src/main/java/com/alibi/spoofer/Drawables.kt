package com.alibi.spoofer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

fun createIntermediateRouteDrawable(context: Context): Drawable {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 3f, 24f, shadowPaint)

    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33F59E0B")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 28f, glowPaint)

    val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
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

    return BitmapDrawable(context.resources, bitmap)
}

fun createRealLocationDrawable(context: Context): Drawable {
    val size = 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 3f, 24f, shadowPaint)

    // Outer Cyan glowing aura
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3306B6D4")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 32f, glowPaint)

    // Vibrant Blue/Cyan main body
    val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0284C7")
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
        color = Color.parseColor("#0369A1")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 4f, innerDot)

    return BitmapDrawable(context.resources, bitmap)
}