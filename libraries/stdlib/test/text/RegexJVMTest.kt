@file:kotlin.jvm.JvmVersion
package test.text

import kotlin.test.*
import org.junit.Test
import test.collections.compare
import test.io.*
import java.util.regex.Pattern


class RegexJVMTest {

    @Test fun matchGroups() {
        val input = "1a 2b 3c"
        val regex = "(\\d)(\\w)".toRegex()

        val matches = regex.findAll(input).toList()
        assertTrue(matches.all { it.groups.size == 3 })
        val m1 = matches[0]
        assertEquals("1a", m1.groups[0]?.value)
        assertEquals(0..1, m1.groups[0]?.range)
        assertEquals("1", m1.groups[1]?.value)
        assertEquals(0..0, m1.groups[1]?.range)
        assertEquals("a", m1.groups[2]?.value)
        assertEquals(1..1, m1.groups[2]?.range)

        val m2 = matches[1]
        assertEquals("2", m2.groups[1]?.value)
        assertEquals(3..3, m2.groups[1]?.range)
        assertEquals("b", m2.groups[2]?.value)
        assertEquals(4..4, m2.groups[2]?.range)
    }


    private fun compareRegex(expected: Regex, actual: Regex) = compare(expected, actual) {
        propertyEquals(Regex::pattern)
        propertyEquals(Regex::options)
        propertyEquals("flags") { toPattern().flags() }
    }

    private fun equivalentAfterDeserialization(regex: Regex) = compareRegex(regex, serializeAndDeserialize(regex))

    @Test fun serializeDeserializeRegex() {
        equivalentAfterDeserialization(Regex(""))
        equivalentAfterDeserialization(Regex("\\w+"))
        equivalentAfterDeserialization(Regex("\\w+", RegexOption.IGNORE_CASE))
        equivalentAfterDeserialization(Regex("\\w+", setOf(RegexOption.LITERAL, RegexOption.MULTILINE)))
        equivalentAfterDeserialization(Pattern.compile("\\w+", Pattern.UNICODE_CASE).toRegex())
    }

    @Test fun deserializeRegexFromHex() {
        val expected = Regex("\\w+", RegexOption.IGNORE_CASE)
        // println(serializeToHex(expected))
        val deserialized = deserializeFromHex<Regex>("ac ed 00 05 73 72 00 11 6b 6f 74 6c 69 6e 2e 74 65 78 74 2e 52 65 67 65 78 00 00 00 00 00 00 00 01 03 00 00 78 70 77 09 00 03 5c 77 2b 00 00 00 42 78")
        compareRegex(expected, deserialized)
    }
}
