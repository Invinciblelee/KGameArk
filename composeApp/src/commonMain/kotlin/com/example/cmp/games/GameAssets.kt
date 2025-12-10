package com.example.cmp.games

import com.game.engine.asset.AtlasKey
import com.game.engine.asset.ImageKey
import com.game.engine.asset.MusicKey
import com.game.engine.asset.SoundKey

object GameAssets {
    object Image {
        val Background = ImageKey("drawable/stormplane_background2.jpg")
        val Player = ImageKey("drawable/image.jpeg")
    }
    object Sound {
        val Eat = SoundKey("files/eat.mp3")
    }
    object Music {
        val BGM = MusicKey("files/bgm4.mp3")
    }

    object Atlas {
        val Walk = AtlasKey("files/Walk.json")
    }
}