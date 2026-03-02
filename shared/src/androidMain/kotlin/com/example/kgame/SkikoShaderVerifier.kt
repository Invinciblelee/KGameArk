package com.example.kgame

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import androidx.core.graphics.createBitmap

@Composable
fun SkikoShaderVerifier() {
    // 这是一段简单的 AGSL 代码：生成一个彩虹渐变
    val sksl = """
        uniform float uTime;
        vec4 main(vec2 pos) {
            vec2 uv = pos / vec2(800.0, 600.0);
            return vec4(uv.x, uv.y, sin(uTime) * 0.5 + 0.5, 1.0);
        }
    """.trimIndent()

    val skiaBitmap = remember {
        try {
            // 1. 编译 Shader
            val effect = RuntimeEffect.makeForShader(sksl)
            val builder = RuntimeShaderBuilder(effect)
            builder.uniform("uTime", 1.0f)

            // 2. 创建一个 Skia 离屏 Canvas 进行验证
            val bitmap = org.jetbrains.skia.Bitmap()
            bitmap.allocPixels(ImageInfo.makeN32(200, 200, ColorAlphaType.PREMUL))
            val canvas = Canvas(bitmap)

            val paint = org.jetbrains.skia.Paint().apply {
                shader = builder.makeShader()
            }

            canvas.drawRect(org.jetbrains.skia.Rect.makeWH(200f, 200f), paint)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    if (skiaBitmap != null) {
        val androidBitmap = createBitmap(skiaBitmap.width, skiaBitmap.height)

        androidBitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(skiaBitmap.readPixels()!!))


        // 如果编译成功并绘制，将其显示在 Compose Canvas 上
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 将 Skia 的 Bitmap 转换为 Compose 可用的 ImageBitmap
            drawImage(androidBitmap.asImageBitmap())
        }
    }
}