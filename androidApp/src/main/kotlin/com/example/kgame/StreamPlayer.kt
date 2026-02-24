//package com.example.kgame
//
//import android.opengl.GLSurfaceView
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.viewinterop.AndroidView
//import kotlinx.coroutines.flow.Flow
//import java.nio.ByteBuffer
//
//@Composable
//fun StreamPlayer(
//    modifier: Modifier = Modifier,
//    scaleType: StreamRenderer.ScaleType = StreamRenderer.ScaleType.FitCenter,
//    packetFlow: Flow<Pair<ByteBuffer, Long>>
//) {
//    var renderer by remember { mutableStateOf<StreamRenderer?>(null) }
//
//    LaunchedEffect(scaleType) {
//        renderer?.scaleType = scaleType
//    }
//
//    LaunchedEffect(packetFlow) {
//        packetFlow.collect { (data, ptsMs) ->
//            renderer?.feedPacket(data, ptsMs)
//        }
//    }
//
//    AndroidView(
//        modifier = modifier,
//        factory = { ctx ->
//            GLSurfaceView(ctx).apply {
//                setEGLContextClientVersion(2)
//                renderer = StreamRenderer(this).apply {
//                    this.scaleType = scaleType
//                }
//                setRenderer(renderer)
//                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
//            }
//        },
//        onRelease = {
//            renderer?.release()
//            renderer = null
//        }
//    )
//}