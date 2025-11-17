package com.example.cmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform