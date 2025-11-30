package com.game.engine.utils

import androidx.compose.animation.core.spring
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

fun JsonObject.getString(key: String): String {
    return this.getValue(key).jsonPrimitive.content
}

fun JsonObject.getInt(key: String): Int {
    return this.getValue(key).jsonPrimitive.int
}

fun JsonObject.getLong(key: String): Long {
    return this.getValue(key).jsonPrimitive.long
}

fun JsonObject.getFloat(key: String): Float {
    return this.getValue(key).jsonPrimitive.float
}

fun JsonObject.getDouble(key: String): Double {
    return this.getValue(key).jsonPrimitive.double
}

fun JsonObject.getBoolean(key: String): Boolean {
    return this.getValue(key).jsonPrimitive.boolean
}

fun JsonObject.getArray(key: String): JsonArray {
    return this.getValue(key).jsonArray
}

fun JsonObject.getObject(key: String): JsonObject {
    return this.getValue(key).jsonObject
}