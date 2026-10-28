package app.sthira.engine.json

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URISyntaxException

/**
 * Structural validation of external JSON (the role zod plays in the TypeScript engine).
 * Every reader appends "path: message" issues to [sink] and returns null on failure, so one pass
 * reports all problems. Messages are this port's own: the golden file only fixes semantic messages.
 */
internal typealias Sink = MutableList<String>

internal enum class NumberRule { ANY, POSITIVE, INT, NONNEGATIVE_INT }

/** Parses JSON text; a syntax error becomes one issue instead of an exception. */
internal fun parseJsonText(text: String): Pair<JsonElement?, String?> =
    try {
        Json.parseToJsonElement(text) to null
    } catch (error: SerializationException) {
        null to ": not valid JSON (${error.message ?: "parse error"})"
    } catch (error: IllegalArgumentException) {
        null to ": not valid JSON (${error.message ?: "parse error"})"
    }

internal fun childPath(path: String, key: String): String = if (path.isEmpty()) key else "$path.$key"

private fun JsonElement?.kindName(): String = when {
    this == null -> "undefined"
    this is JsonNull -> "null"
    this is JsonObject -> "object"
    this is JsonArray -> "array"
    this is JsonPrimitive && isString -> "string"
    this is JsonPrimitive && (content == "true" || content == "false") -> "boolean"
    else -> "number"
}

private fun expected(path: String, what: String, element: JsonElement?, sink: Sink) {
    sink.add("$path: expected $what, received ${element.kindName()}")
}

internal fun asObject(element: JsonElement?, path: String, sink: Sink): JsonObject? {
    if (element is JsonObject) return element
    expected(path, "object", element, sink)
    return null
}

internal fun asArray(element: JsonElement?, path: String, sink: Sink, minSize: Int = 0): JsonArray? {
    if (element !is JsonArray) {
        expected(path, "array", element, sink)
        return null
    }
    if (element.size < minSize) {
        sink.add("$path: array must contain at least $minSize element(s)")
        return null
    }
    return element
}

/** A string of at least one character; an absent key is allowed only when [optional]. */
internal fun readString(obj: JsonObject, key: String, path: String, sink: Sink, optional: Boolean = false): String? {
    val here = childPath(path, key)
    val element = obj[key]
    if (element == null && optional) return null
    if (element !is JsonPrimitive || element is JsonNull || !element.isString) {
        expected(here, "string", element, sink)
        return null
    }
    if (element.content.isEmpty()) {
        sink.add("$here: string must contain at least 1 character")
        return null
    }
    return element.content
}

/** A string that must be one of [allowed]. */
internal fun readEnum(obj: JsonObject, key: String, path: String, sink: Sink, allowed: List<String>): String? {
    val here = childPath(path, key)
    val element = obj[key]
    val value = (element as? JsonPrimitive)?.takeIf { it.isString && it !is JsonNull }?.content
    if (value != null && value in allowed) return value
    sink.add("$here: expected one of ${allowed.joinToString(", ")}, received ${value ?: element.kindName()}")
    return null
}

internal fun readNumber(obj: JsonObject, key: String, path: String, sink: Sink, rule: NumberRule = NumberRule.ANY): Double? {
    val here = childPath(path, key)
    val element = obj[key]
    val number = numberOf(element)
    if (number == null) {
        expected(here, "number", element, sink)
        return null
    }
    val problem = numberProblem(number, rule) ?: return number
    sink.add("$here: $problem")
    return null
}

internal fun readInt(obj: JsonObject, key: String, path: String, sink: Sink, nonNegative: Boolean = false): Int? {
    val rule = if (nonNegative) NumberRule.NONNEGATIVE_INT else NumberRule.INT
    val value = readNumber(obj, key, path, sink, rule) ?: return null
    if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
        sink.add("${childPath(path, key)}: integer out of range")
        return null
    }
    return value.toInt()
}

/** An absolute URL with a scheme and host (zod's z.url()). */
internal fun readUrl(obj: JsonObject, key: String, path: String, sink: Sink): String? {
    val value = readString(obj, key, path, sink) ?: return null
    val valid = try {
        val uri = URI(value)
        uri.isAbsolute && !uri.host.isNullOrEmpty()
    } catch (error: URISyntaxException) {
        false
    }
    if (valid) return value
    sink.add("${childPath(path, key)}: invalid URL")
    return null
}

private fun numberOf(element: JsonElement?): Double? {
    if (element !is JsonPrimitive || element is JsonNull || element.isString) return null
    if (element.content == "true" || element.content == "false") return null
    return element.content.toDoubleOrNull()?.takeIf { it.isFinite() }
}

private fun numberProblem(value: Double, rule: NumberRule): String? {
    val isInt = value == Math.floor(value)
    return when (rule) {
        NumberRule.ANY -> null
        NumberRule.POSITIVE -> if (value > 0) null else "number must be greater than 0"
        NumberRule.INT -> if (isInt) null else "expected integer, received float"
        NumberRule.NONNEGATIVE_INT -> when {
            !isInt -> "expected integer, received float"
            value < 0 -> "number must be greater than or equal to 0"
            else -> null
        }
    }
}
