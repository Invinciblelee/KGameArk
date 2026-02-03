package com.kgame.engine.utils.internal

import kotlinx.serialization.json.*

internal fun JsonObject.getString(key: String, defaultValue: String = ""): String {
    return this[key]?.jsonPrimitive?.content ?: defaultValue
}

internal fun JsonObject.getInt(key: String, defaultValue: Int = 0): Int {
    return this[key]?.jsonPrimitive?.int ?: defaultValue
}

internal fun JsonObject.getLong(key: String, defaultValue: Long = 0L): Long {
    return this[key]?.jsonPrimitive?.long ?: defaultValue
}

internal fun JsonObject.getFloat(key: String, defaultValue: Float = 0f): Float {
    return this[key]?.jsonPrimitive?.float ?: defaultValue
}

internal fun JsonObject.getDouble(key: String, defaultValue: Double = 0.0): Double {
    return this[key]?.jsonPrimitive?.double ?: defaultValue
}

internal fun JsonObject.getBoolean(key: String, defaultValue: Boolean = false): Boolean {
    return this[key]?.jsonPrimitive?.booleanOrNull ?: defaultValue
}

internal fun JsonObject.getArray(key: String): JsonArray {
    return this[key]?.jsonArray ?: buildJsonArray { }
}

internal fun JsonObject.getArrayOrNull(key: String): JsonArray? {
    return this[key]?.jsonArray
}

internal fun JsonObject.getObject(key: String): JsonObject {
    return this[key]?.jsonObject ?: buildJsonObject { }
}

internal fun JsonObject.getObjectOrNull(key: String): JsonObject? {
    return this[key]?.jsonObject
}