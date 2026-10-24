package br.com.nider.biblioteca

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private val Fundo = Color(0xFF000000)

@Composable
fun FotoPager(pages: List<ImageBitmap>, start: Int, onClose: () -> Unit) {
    if (pages.isEmpty()) return
    val pager = rememberPagerState(initialPage = start.coerceIn(0, pages.lastIndex)) { pages.size }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().background(Fundo)) { page ->
        FotoFrame(pages[page], onClose)
    }
}

@Composable
fun FotoFrame(bitmap: ImageBitmap, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var twist by remember { mutableFloatStateOf(0f) }
    var quarter by remember { mutableIntStateOf(0) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var chrome by remember { mutableStateOf(true) }
    var entered by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(quarter * 90f, tween(280), label = "giro")
    val enter by animateFloatAsState(if (entered) 1f else 0.9f, tween(220), label = "abre")
    val bars by animateFloatAsState(if (chrome) 1f else 0f, tween(200), label = "barras")
    LaunchedEffect(Unit) { entered = true }
    val gesture = rememberTransformableState { zoom, offset, rotation ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        twist += rotation
        if (scale > 1.05f) pan += offset else pan = Offset.Zero
        if (scale > 1.05f) dragY = 0f
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Fundo)
            .pointerInput(scale) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dy -> if (scale <= 1.05f && dy > 0f) dragY += dy },
                    onDragEnd = {
                        if (dragY > 180f) onClose() else dragY = 0f
                    },
                )
            },
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val away = (dragY / 900f).coerceIn(0f, 0.2f)
                    scaleX = scale * enter * (1f - away)
                    scaleY = scale * enter * (1f - away)
                    rotationZ = twist + turn
                    translationX = pan.x
                    translationY = pan.y + dragY
                }
                .transformable(gesture)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chrome = !chrome },
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                pan = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                },
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .graphicsLayer { alpha = bars },
        ) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.fechar), color = Color.White) }
            TextButton(onClick = { quarter += 1 }) { Text(stringResource(R.string.girar), color = Color.White) }
        }
    }
}
