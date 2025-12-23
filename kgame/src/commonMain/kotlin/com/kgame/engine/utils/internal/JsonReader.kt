package com.kgame.engine.utils.internal

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

internal fun JsonObject.getString(key: String): String {
    return this.getValue(key).jsonPrimitive.content
}

internal fun JsonObject.getInt(key: String): Int {
    return this.getValue(key).jsonPrimitive.int
}

internal fun JsonObject.getLong(key: String): Long {
    return this.getValue(key).jsonPrimitive.long
}

internal fun JsonObject.getFloat(key: String): Float {
    return this.getValue(key).jsonPrimitive.float
}

internal fun JsonObject.getDouble(key: String): Double {
    return this.getValue(key).jsonPrimitive.double
}

internal fun JsonObject.getBoolean(key: String): Boolean {
    return this.getValue(key).jsonPrimitive.boolean
}

internal fun JsonObject.getArray(key: String): JsonArray {
    return this.getValue(key).jsonArray
}

internal fun JsonObject.getObject(key: String): JsonObject {
    return this.getValue(key).jsonObject
}