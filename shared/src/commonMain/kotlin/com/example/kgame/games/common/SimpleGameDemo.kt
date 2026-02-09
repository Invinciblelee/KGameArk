package com.example.kgame.games.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import com.example.kgame.games.shader.BlueSky
import com.kgame.engine.core.KSimpleGame
import com.kgame.engine.ui.ActiveRectangle
import com.kgame.plugins.components.InfiniteRepeatable
import com.kgame.plugins.components.PathAnimation
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.ScaleAnimation
import com.kgame.plugins.components.Transform
import com.kgame.plugins.components.Tween
import com.kgame.plugins.visuals.shapes.RectangleVisual

@Composable
fun SimpleGameDemo() {
    KSimpleGame(
        modifier = Modifier.fillMaxSize(),
    ) {
        val visual = RectangleVisual(color = Color.Red, size = Size(width = 100f, height = 100f))

        world {
            useDefaultSystems()

            spawn {
                entity {
                    // 1. Define a curved path
                    val centerPath = Path().apply {
                        // Start at the left side of the "orbit"
                        moveTo(-200f, 0f)
                        // A simple circular or oval orbit using cubic Bézier
                        // cubicTo(control1, control2, end)
                        cubicTo(-200f, -200f, 200f, -200f, 200f, 0f)
                        cubicTo(200f, 200f, -200f, 200f, -200f, 0f)
                        close()
                    }

                    +Transform()

                    // 2. Add the new PathAnimation
                    +PathAnimation(
                        name = "orbit",
                        path = centerPath,
                        // 2 seconds per cycle, linear easing for constant speed
                        spec = InfiniteRepeatable(
                            animation = Tween(duration = 2f, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        orientToPath = true, // Enable automatic rotation
                        rotationOffset = 90f // Adjust if your sprite faces "up" by default
                    )

                    // 3. Keep your existing scale animation to see them combined
                    +ScaleAnimation(
                        name = "scale",
                        from = 1f,
                        to = 1.5f,
                        spec = InfiniteRepeatable(Tween(1f), RepeatMode.Reverse)
                    )

                    +Renderable(visual = visual)
                }
            }
        }

        onUpdate {
            if (input.isKeyJustPressed(Key.Spacebar)) {
                visual.color =
                    if (visual.color == Color.Red) Color.Yellow else Color.Red
            }
        }

        onBackgroundUI {
            val shader = remember { BlueSky() }
            ActiveRectangle(shader)
        }

        onForegroundUI {
            Button(
                onClick = {
                    visual.color =
                        if (visual.color == Color.Red) Color.Yellow else Color.Red
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp)
            ) {
                Text(
                    text = "Hello World",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}