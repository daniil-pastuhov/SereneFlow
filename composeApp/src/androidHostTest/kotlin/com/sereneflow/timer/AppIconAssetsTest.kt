package com.sereneflow.timer

import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.platform.androidLauncherAlias
import com.sereneflow.timer.platform.iosIconName
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every theme's launcher icon exists on both platforms under the name AppIconSwitcher uses, so
 * renaming one side (tools/generate_icons.swift, iosApp/project.yml, androidApp's manifest)
 * without the others fails here instead of silently on a device.
 */
class AppIconAssetsTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun defaultIconIsThePrimaryIcon() {
        assertNull(AppThemeMode.ZEN_SAGE.iosIconName)
        // 1.0's launcher component, which existing home-screen shortcuts point at.
        assertEquals("com.sereneflow.timer.MainActivity", AppThemeMode.ZEN_SAGE.androidLauncherAlias)
        assertTrue("android:name=\".MainActivity\"" in manifest())
    }

    @Test
    fun everyIosAlternateIconIsGeneratedAndRegistered() {
        val projectYml = File(root, "iosApp/project.yml").readText()
        val registered = Regex("""ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES: "([^"]*)"""")
            .find(projectYml)?.groupValues?.get(1)?.split(" ")?.toSet().orEmpty()
        val alternates = AppThemeMode.entries.mapNotNull { it.iosIconName }.toSet()

        assertEquals(alternates, registered, "project.yml alternate icon names")
        for (name in alternates + "AppIcon") {
            val iconSet = File(root, "iosApp/iosApp/Assets.xcassets/$name.appiconset/Contents.json")
            assertTrue(iconSet.exists(), "missing $iconSet")
        }
    }

    @Test
    fun everyAndroidAliasIsDeclaredWithExactlyOneEnabled() {
        val aliases = Regex("""<activity-alias(.*?)</activity-alias>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest()).map { it.groupValues[1] }.toList()
        val byName = aliases.associateBy { Regex("""android:name="\.(\w+)"""").find(it)!!.groupValues[1] }

        assertEquals(
            AppThemeMode.entries.map { it.androidLauncherAlias.substringAfterLast('.') }.toSet(),
            byName.keys,
            "manifest launcher aliases"
        )
        assertEquals(1, aliases.count { "android:enabled=\"true\"" in it }, "aliases enabled by default")
        for (alias in aliases) {
            val icon = Regex("""android:icon="@mipmap/(\w+)"""").find(alias)!!.groupValues[1]
            assertTrue(File(root, "androidApp/src/main/res/mipmap-anydpi-v26/$icon.xml").exists(), "missing $icon")
        }
    }

    private fun manifest() = File(root, "androidApp/src/main/AndroidManifest.xml").readText()
}
