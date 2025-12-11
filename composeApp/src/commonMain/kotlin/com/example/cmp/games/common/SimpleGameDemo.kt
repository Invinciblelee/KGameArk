package com.example.cmp.games.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import com.game.engine.core.GameEnvironment
import com.game.engine.core.PlatformContext
import com.game.engine.core.KSimpleGame
import com.game.engine.ui.Rectangle

@Composable
fun SimpleGameDemo(environment: GameEnvironment) {
    KSimpleGame(
        environment = environment,
        modifier = Modifier.fillMaxSize(),
    ) {
        var color = Color.Red

        onUpdate {
            if (input.isKeyDown(Key.Spacebar)) {
                color = if (color == Color.Red) Color.Yellow else Color.Red
            }
        }

        onRender { drawCircle(color, radius = 50f) }

        onBackgroundUI { Rectangle(Color.Blue) }

        onForegroundUI {
            Button(
                onClick = {} ,
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