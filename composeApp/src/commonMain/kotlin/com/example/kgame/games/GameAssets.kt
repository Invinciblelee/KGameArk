package com.example.kgame.games

import com.kgame.engine.asset.AtlasKey
import com.kgame.engine.asset.ImageKey
import com.kgame.engine.asset.MusicKey
import com.kgame.engine.asset.SoundKey
import com.kgame.engine.asset.TiledMapKey
import com.kgame.plugins.components.TiledMap

object GameAssets {
    object Image {
        val Background = ImageKey("drawable/stormplane_background.jpg")
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

    object TiledMap {
        val Example = TiledMapKey("files/maps/desert.tmx")
    }
}