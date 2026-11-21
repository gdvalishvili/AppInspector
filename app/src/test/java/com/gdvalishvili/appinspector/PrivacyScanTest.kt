package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.privacy.ApkClassScanner
import com.gdvalishvili.appinspector.data.privacy.DexClassReader
import com.gdvalishvili.appinspector.data.privacy.TrackerDef
import com.gdvalishvili.appinspector.data.privacy.TrackerMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PrivacyScanTest {

    /** Minimal valid DEX: header + string_ids + type_ids + class_defs + string data. */
    private fun buildDex(descriptors: List<String>): ByteArray {
        val n = descriptors.size
        val stringIdsOff = 0x70
        val typeIdsOff = stringIdsOff + n * 4
        val classDefsOff = typeIdsOff + n * 4
        val dataOff = classDefsOff + n * 32
        val data = descriptors.map { byteArrayOf(it.length.toByte()) + it.toByteArray() + byteArrayOf(0) }
        val buf = ByteBuffer.allocate(dataOff + data.sumOf { it.size }).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0, "dex\n035\u0000".toByteArray())
        buf.putInt(0x38, n); buf.putInt(0x3C, stringIdsOff)
        buf.putInt(0x40, n); buf.putInt(0x44, typeIdsOff)
        buf.putInt(0x60, n); buf.putInt(0x64, classDefsOff)
        var p = dataOff
        data.forEachIndexed { i, bytes ->
            buf.putInt(stringIdsOff + i * 4, p)
            buf.putInt(typeIdsOff + i * 4, i)
            buf.putInt(classDefsOff + i * 32, i)
            buf.put(p, bytes)
            p += bytes.size
        }
        return buf.array()
    }

    @Test
    fun readsClassNames() {
        val dex = buildDex(listOf("Lcom/flurry/android/Agent;", "Lorg/example/Main;"))
        assertEquals(listOf("com.flurry.android.Agent", "org.example.Main"), DexClassReader.classNames(dex))
    }

    @Test
    fun malformedInputIsSafe() {
        assertTrue(DexClassReader.classNames(ByteArray(10)).isEmpty())
        assertTrue(DexClassReader.classNames("not a dex file at all, just text".repeat(10).toByteArray()).isEmpty())
        val truncated = buildDex(listOf("Lcom/a/B;")).copyOf(0x80)
        assertTrue(DexClassReader.classNames(truncated).isEmpty())
    }

    @Test
    fun scansAllDexInApk() {
        val apk = File.createTempFile("test", ".apk").apply { deleteOnExit() }
        ZipOutputStream(FileOutputStream(apk)).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex")); zip.write(buildDex(listOf("Lcom/a/One;"))); zip.closeEntry()
            zip.putNextEntry(ZipEntry("classes2.dex")); zip.write(buildDex(listOf("Lcom/b/Two;"))); zip.closeEntry()
            zip.putNextEntry(ZipEntry("assets/classes.dex")); zip.write(buildDex(listOf("Lcom/c/Hidden;"))); zip.closeEntry()
        }
        val result = ApkClassScanner.scan(listOf(apk.path))
        assertEquals(2, result.dexFiles) // assets/ is not app code
        assertEquals(setOf("com.a.One", "com.b.Two"), result.classNames.toSet())
    }

    private val flurry = TrackerDef(1, "Flurry", listOf("Analytics"), listOf("com.flurry."), "")
    private val tealium = TrackerDef(2, "Tealium", listOf("Analytics"), listOf(".tealium."), "")
    private val ads = TrackerDef(3, "Some Ads", listOf("Advertisement"), listOf("com.ads.sdk.", "net.ads."), "")

    @Test
    fun matchesPrefixAndContains() {
        val matcher = TrackerMatcher(listOf(flurry, tealium, ads))
        val found = matcher.match(
            listOf("com.flurry.android.Agent", "com.vendor.tealium.Lib", "net.ads.Banner", "org.example.Main")
        )
        assertEquals(listOf("Flurry", "Some Ads", "Tealium"), found.map { it.name })
    }

    @Test
    fun noFalseMatches() {
        val matcher = TrackerMatcher(listOf(flurry, tealium, ads))
        assertTrue(matcher.match(listOf("com.flurryx.Other", "com.adsx.sdk.A", "org.example.Main")).isEmpty())
        assertTrue(matcher.match(emptyList()).isEmpty())
    }
}