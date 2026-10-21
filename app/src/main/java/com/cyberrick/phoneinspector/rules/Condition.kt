package com.cyberrick.phoneinspector.rules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * The rule condition language. Deliberately tiny so rules stay reviewable as data:
 * {"fact":f,"eq":v} {"fact":f,"in":[..]} {"fact":f,"gt":n} {"not":c} {"all":[..]} {"any":[..]}.
 * A missing or null fact never satisfies eq, in or gt.
 */
sealed class Condition {
    abstract fun evaluate(facts: Map<String, Any?>): Boolean

    data class Eq(
        val fact: String,
        val value: JsonPrimitive,
    ) : Condition() {
        override fun evaluate(facts: Map<String, Any?>): Boolean = facts[fact]?.let { sameValue(it, value) } ?: false
    }

    data class In(
        val fact: String,
        val values: List<JsonPrimitive>,
    ) : Condition() {
        override fun evaluate(facts: Map<String, Any?>): Boolean = facts[fact]?.let { f -> values.any { sameValue(f, it) } } ?: false
    }

    data class Gt(
        val fact: String,
        val threshold: Double,
    ) : Condition() {
        override fun evaluate(facts: Map<String, Any?>): Boolean = (facts[fact] as? Number)?.toDouble()?.let { it > threshold } ?: false
    }

    data class Not(
        val inner: Condition,
    ) : Condition() {
        override fun evaluate(facts: Map<String, Any?>): Boolean = !inner.evaluate(facts)
    }

    data class AllOf(
        val parts: List<Condition>,
    ) : Condition() {
        override fun evaluate(facts: Map<String, Any?>): Boolean = parts.all { it.evaluate(facts) }
    }

    data class AnyOf(
        val parts: List<Condition>,
    ) : Condition() {
        override fun evaluate(facts: Map<String, Any?>): Boolean = parts.any { it.evaluate(facts) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): Condition = fromElement(json.parseToJsonElement(text))

        fun fromElement(element: JsonElement): Condition {
            val obj = element as? JsonObject ?: throw IllegalArgumentException("condition must be an object: $element")
            val keys = obj.keys
            return when {
                "all" in keys -> AllOf(obj.getValue("all").jsonArray.map { fromElement(it) })
                "any" in keys -> AnyOf(obj.getValue("any").jsonArray.map { fromElement(it) })
                "not" in keys -> Not(fromElement(obj.getValue("not")))
                "fact" in keys -> factCondition(obj)
                else -> throw IllegalArgumentException("unknown condition keys $keys")
            }
        }

        private fun factCondition(obj: JsonObject): Condition {
            val fact = obj.getValue("fact").jsonPrimitive.content
            val op =
                (obj.keys - "fact").singleOrNull()
                    ?: throw IllegalArgumentException("condition on $fact needs exactly one operator, got ${obj.keys}")
            val operand = obj.getValue(op)
            return when (op) {
                "eq" -> Eq(fact, operand.jsonPrimitive)
                "in" -> In(fact, (operand as JsonArray).map { it.jsonPrimitive })
                "gt" -> Gt(fact, operand.jsonPrimitive.doubleOrNull ?: throw IllegalArgumentException("gt needs a number for $fact"))
                else -> throw IllegalArgumentException("unknown operator '$op' on fact $fact")
            }
        }

        private fun sameValue(
            fact: kotlin.Any,
            literal: JsonPrimitive,
        ): Boolean =
            when (fact) {
                is Boolean -> literal.booleanOrNull == fact
                is Number -> literal.doubleOrNull == fact.toDouble()
                is String -> literal.isString && literal.content == fact
                else -> literal != JsonNull && literal.content == fact.toString()
            }
    }
}
