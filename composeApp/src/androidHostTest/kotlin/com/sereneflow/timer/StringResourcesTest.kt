package com.sereneflow.timer

import kotlinx.coroutines.runBlocking
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared app's strings (composeResources/values/strings.xml): each one is used, each plural
 * has the forms English needs, and the stand-ins the engine tests use for the spoken phrases
 * ([EnglishPhrases]) say exactly what the resources say. Host tests can't load Compose resources,
 * so the file is read and formatted here the way Compose does (positional %1$s / %1$d only).
 */
class StringResourcesTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }
    private val composeApp = File(root, "composeApp")

    private val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File(composeApp, "src/commonMain/composeResources/values/strings.xml"))

    private val strings: Map<String, String> = document.getElementsByTagName("string").let { nodes ->
        (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private val plurals: Map<String, Map<String, String>> = document.getElementsByTagName("plurals").let { nodes ->
        (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as Element
            val items = element.getElementsByTagName("item")
            element.getAttribute("name") to (0 until items.length).associate { j ->
                val item = items.item(j) as Element
                item.getAttribute("quantity") to item.textContent
            }
        }
    }

    private fun format(template: String, vararg args: Any): String =
        Regex("""%(\d+)\$[ds]""").replace(template) { args[it.groupValues[1].toInt() - 1].toString() }

    private fun string(name: String, vararg args: Any): String =
        format(checkNotNull(strings[name]) { "no string $name" }, *args)

    /** English plural rules: "one" for 1, "other" otherwise. */
    private fun plural(name: String, quantity: Int, vararg args: Any): String {
        val forms = checkNotNull(plurals[name]) { "no plurals $name" }
        return format(forms.getValue(if (quantity == 1) "one" else "other"), *args)
    }

    /** As text/DurationText.kt's spokenDuration puts it together. */
    private fun spokenDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes > 0 && seconds > 0 -> string(
                "duration_minutes_seconds",
                plural("duration_minutes", minutes, minutes),
                plural("duration_seconds", seconds, seconds)
            )
            minutes > 0 -> plural("duration_minutes", minutes, minutes)
            else -> plural("duration_seconds", seconds, seconds)
        }
    }

    @Test
    fun everyPluralHasOneAndOther() {
        plurals.forEach { (name, forms) ->
            assertTrue("one" in forms && "other" in forms, "$name has ${forms.keys}")
        }
    }

    @Test
    fun everyStringIsUsed() {
        val code = composeApp.resolve("src").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "Main" in it.path }
            .joinToString("\n") { it.readText() }
        val used = Regex("""Res\.(?:string|plurals)\.(\w+)""").findAll(code).map { it.groupValues[1] }.toSet()
        val unused = (strings.keys + plurals.keys) - used
        assertTrue(unused.isEmpty(), "unused strings: $unused")
    }

    @Test
    fun durationsAreSpokenInWords() {
        assertEquals("2 minutes", spokenDuration(120))
        assertEquals("1 minute", spokenDuration(60))
        assertEquals("1 minute 30 seconds", spokenDuration(90))
        assertEquals("2 minutes 1 second", spokenDuration(121))
        assertEquals("45 seconds", spokenDuration(45))
        assertEquals("1 second", spokenDuration(1))
        listOf(1, 45, 60, 90, 120, 121, 3600).forEach {
            assertEquals(englishSpokenDuration(it), spokenDuration(it))
        }
    }

    @Test
    fun engineTestPhrasesMatchTheResources() = runBlocking {
        assertEquals(
            string("speech_step", "Breathe", spokenDuration(90)),
            EnglishPhrases.stepStart("Breathe", 90, number = null)
        )
        assertEquals(
            string("speech_step_numbered", 2, "Breathe", spokenDuration(10)),
            EnglishPhrases.stepStart("Breathe", 10, number = 2)
        )
        assertEquals(string("speech_reminder", "Breathe", 50), EnglishPhrases.reminder("Breathe", 50))
        assertEquals(string("speech_routine_complete"), EnglishPhrases.routineComplete())
    }

    @Test
    fun testSeedMatchesTheResources() = runBlocking {
        val seed = TestSeedRoutines().single()
        assertEquals(string("seed_title"), seed.title)
        assertEquals((1..4).map { string("seed_step_$it") }, seed.steps.map { it.title })
    }
}
