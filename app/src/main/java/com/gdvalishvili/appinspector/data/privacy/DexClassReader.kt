package com.gdvalishvili.appinspector.data.privacy

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Reads the names of classes defined in a DEX file (class_defs -> type_ids -> string_ids).
 * Pure Kotlin, no Android: unit-tested. Malformed input returns an empty list, never throws.
 */
object DexClassReader {

    private const val HEADER_SIZE = 0x70
    private const val CLASS_DEF_SIZE = 32

    fun classNames(dex: ByteArray): List<String> {
        if (dex.size < HEADER_SIZE || !isDex(dex)) return emptyList()
        return try {
            val buf = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
            val stringIdsSize = buf.getInt(0x38)
            val stringIdsOff = buf.getInt(0x3C)
            val typeIdsSize = buf.getInt(0x40)
            val typeIdsOff = buf.getInt(0x44)
            val classDefsSize = buf.getInt(0x60)
            val classDefsOff = buf.getInt(0x64)
            if (!fits(dex, stringIdsOff, stringIdsSize, 4) || !fits(dex, typeIdsOff, typeIdsSize, 4) ||
                !fits(dex, classDefsOff, classDefsSize, CLASS_DEF_SIZE)
            ) return emptyList()

            val out = ArrayList<String>(classDefsSize)
            for (i in 0 until classDefsSize) {
                val typeIdx = buf.getInt(classDefsOff + i * CLASS_DEF_SIZE)
                if (typeIdx !in 0 until typeIdsSize) continue
                val stringIdx = buf.getInt(typeIdsOff + typeIdx * 4)
                if (stringIdx !in 0 until stringIdsSize) continue
                val dataOff = buf.getInt(stringIdsOff + stringIdx * 4)
                val descriptor = readString(dex, dataOff) ?: continue
                toDotted(descriptor)?.let(out::add)
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** "Lcom/foo/Bar;" -> "com.foo.Bar" */
    fun toDotted(descriptor: String): String? =
        if (descriptor.length > 2 && descriptor[0] == 'L' && descriptor.last() == ';') {
            descriptor.substring(1, descriptor.length - 1).replace('/', '.')
        } else null

    private fun isDex(b: ByteArray) =
        b[0] == 0x64.toByte() && b[1] == 0x65.toByte() && b[2] == 0x78.toByte() && b[3] == 0x0A.toByte()

    private fun fits(b: ByteArray, off: Int, count: Int, itemSize: Int): Boolean =
        off >= 0 && count >= 0 && off.toLong() + count.toLong() * itemSize <= b.size

    /** string_data_item: uleb128 length (skipped), then MUTF-8 bytes ending with 0. */
    private fun readString(b: ByteArray, off: Int): String? {
        if (off !in b.indices) return null
        var p = off
        while (p < b.size && (b[p].toInt() and 0x80) != 0) p++ // uleb128 continuation bytes
        p++ // last uleb128 byte
        val start = p
        while (p < b.size && b[p] != 0.toByte()) p++
        if (p >= b.size) return null
        return String(b, start, p - start, Charsets.UTF_8)
    }
}

/** Collects class names from all classes*.dex in an app's APK files (base + splits). */
object ApkClassScanner {

    /** Bigger DEX files are skipped (memory safety on huge apps). */
    const val MAX_DEX_BYTES = 30 * 1024 * 1024
    private val DEX_ENTRY = Regex("^classes\\d*\\.dex$")

    data class Result(val classNames: List<String>, val dexFiles: Int, val skippedDex: Int)

    fun scan(apkPaths: List<String>): Result {
        val names = ArrayList<String>()
        var dexFiles = 0
        var skipped = 0
        for (path in apkPaths) {
            try {
                ZipFile(path).use { zip ->
                    for (entry in zip.entries().asSequence().filter { DEX_ENTRY.matches(it.name) }) {
                        if (entry.size > MAX_DEX_BYTES) {
                            skipped++
                            continue
                        }
                        val bytes = zip.getInputStream(entry).use { readCapped(it, MAX_DEX_BYTES) }
                        if (bytes == null) {
                            skipped++
                        } else {
                            dexFiles++
                            names += DexClassReader.classNames(bytes)
                        }
                    }
                }
            } catch (e: Exception) {
                // Unreadable APK (rare): skip it
            }
        }
        return Result(names, dexFiles, skipped)
    }

    private fun readCapped(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}