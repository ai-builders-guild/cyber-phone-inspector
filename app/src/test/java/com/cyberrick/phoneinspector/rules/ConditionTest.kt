package com.cyberrick.phoneinspector.rules

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The rule condition language: a JSON tree of eq, in, gt, not, all, any over a flat fact map. */
class ConditionTest {
    private val facts = mapOf("hidden" to true, "installSource" to "SIDELOADED", "minutes" to 400L, "category" to null)

    private fun eval(json: String) = Condition.parse(json).evaluate(facts)

    @Test
    fun `eq compares a fact to a literal`() {
        assertThat(eval("""{"fact":"hidden","eq":true}""")).isTrue()
        assertThat(eval("""{"fact":"installSource","eq":"PLAY_STORE"}""")).isFalse()
    }

    @Test
    fun `in tests membership`() {
        assertThat(eval("""{"fact":"installSource","in":["SIDELOADED","UNKNOWN"]}""")).isTrue()
        assertThat(eval("""{"fact":"installSource","in":["PLAY_STORE"]}""")).isFalse()
    }

    @Test
    fun `gt compares numbers`() {
        assertThat(eval("""{"fact":"minutes","gt":360}""")).isTrue()
        assertThat(eval("""{"fact":"minutes","gt":400}""")).isFalse()
    }

    @Test
    fun `missing or null facts are false for eq and in and gt`() {
        assertThat(eval("""{"fact":"category","eq":"MAPS"}""")).isFalse()
        assertThat(eval("""{"fact":"nope","in":["x"]}""")).isFalse()
        assertThat(eval("""{"fact":"nope","gt":0}""")).isFalse()
    }

    @Test
    fun `all any and not compose`() {
        val json =
            """{"all":[{"fact":"hidden","eq":true},""" +
                """{"any":[{"fact":"installSource","eq":"PLAY_STORE"},{"not":{"fact":"minutes","gt":1000}}]}]}"""

        assertThat(eval(json)).isTrue()
    }

    @Test
    fun `an unknown operator fails loudly at parse time`() {
        val err = runCatching { Condition.parse("""{"fact":"hidden","like":"x"}""") }.exceptionOrNull()

        assertThat(err).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(err?.message).contains("like")
    }
}
