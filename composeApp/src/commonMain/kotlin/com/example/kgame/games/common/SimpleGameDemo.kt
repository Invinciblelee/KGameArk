package com.example.kgame.games.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import com.example.kgame.games.shader.BlueSky
import com.kgame.engine.core.KSimpleGame
import com.kgame.engine.ui.ActiveRectangle
import com.kgame.plugins.components.CircleVisual
import com.kgame.plugins.components.InfiniteRepeatable
import com.kgame.plugins.components.Renderable
import com.kgame.plugins.components.RigidBody
import com.kgame.plugins.components.ScaleAnimation
import com.kgame.plugins.components.Transform
import com.kgame.plugins.systems.AnimationSystem
import com.kgame.plugins.systems.AnimationTickSystem
import com.kgame.plugins.systems.RenderSystem

@Composable
fun SimpleGameDemo() {
    KSimpleGame(
        modifier = Modifier.fillMaxSize(),
    ) {
        val circleVisual = CircleVisual(color = Color.Red, size = 100f)

        world(configuration = {
            systems {
                +AnimationTickSystem()
                +AnimationSystem()
                +RenderSystem()
            }
        }) {
            entity {
                +Transform()
                +RigidBody(velocity = Offset(10f, 0f))
                +ScaleAnimation(from = 1f, to = 2f, spec = InfiniteRepeatable())
                +Renderable(visual = circleVisual)
            }
        }

        onUpdate {
            if (input.isKeyJustPressed(Key.Spacebar)) {
                circleVisual.color =
                    if (circleVisual.color == Color.Red) Color.Yellow else Color.Red
            }
        }

        onBackgroundUI {
            ActiveRectangle(BlueSky())
        }

        onForegroundUI {
            Button(
                onClick = {},
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