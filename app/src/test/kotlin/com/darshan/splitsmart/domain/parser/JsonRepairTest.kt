package com.darshan.splitsmart.domain.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JsonRepairTest {
    @Test
    fun `returns valid json unchanged`() {
        assertThat(JsonRepair.repair("""{"a":1}""")).isEqualTo("""{"a":1}""")
    }

    @Test
    fun `strips markdown code fences and surrounding prose`() {
        assertThat(JsonRepair.repair("Sure! Here is the JSON:\n```json\n{\"a\": 1}\n```\nLet me know.")).isEqualTo("{\"a\": 1}")
        assertThat(JsonRepair.repair("```\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}")
    }

    @Test
    fun `removes trailing commas in objects and arrays`() {
        assertThat(JsonRepair.repair("""{"items":[{"a":1,},{"b":2,},],"t":3,}""")).isEqualTo("""{"items":[{"a":1},{"b":2}],"t":3}""")
    }

    @Test
    fun `replaces smart quotes and NaN or Infinity`() {
        assertThat(JsonRepair.repair("{“a”: ‘x’, \"b\": NaN, \"c\": -Infinity}")).isEqualTo("""{"a": "x", "b": null, "c": null}""")
    }

    @Test
    fun `closes truncated json`() {
        assertThat(JsonRepair.repair("""{"items":[{"name":"Dosa","totalPaise":12000},{"name":"Cof""")).isEqualTo("""{"items":[{"name":"Dosa","totalPaise":12000},{"name":"Cof"}]}""")
        assertThat(JsonRepair.repair("""{"items":[{"name":"Dosa","totalPaise":12000""")).isEqualTo("""{"items":[{"name":"Dosa","totalPaise":12000}]}""")
    }

    @Test
    fun `does not touch commas or braces inside strings`() {
        val input = """{"name":"Chips, salted}","q":1}"""
        assertThat(JsonRepair.repair(input)).isEqualTo(input)
    }

    @Test
    fun `strips line and block comments`() {
        assertThat(JsonRepair.repair("{\"a\":1, // tax\n\"b\":2 /* tip */}")).isEqualTo("{\"a\":1, \n\"b\":2 }")
    }

    @Test
    fun `rejects input with no object at all or that is too large`() {
        assertThat(JsonRepair.repair("I could not read this receipt.")).isNull()
        assertThat(JsonRepair.repair("")).isNull()
        assertThat(JsonRepair.repair("[1,2,3]")).isNull()
        assertThat(JsonRepair.repair("{" + "\"a\":1,".repeat(100_000) + "}")).isNull()
    }
}
