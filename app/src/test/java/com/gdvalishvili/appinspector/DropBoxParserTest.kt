package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.logs.CrashKind
import com.gdvalishvili.appinspector.data.logs.DropBoxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DropBoxParserTest {

    @Test
    fun kinds() {
        assertEquals(CrashKind.CRASH, DropBoxParser.kindOf("data_app_crash"))
        assertEquals(CrashKind.CRASH, DropBoxParser.kindOf("system_app_crash"))
        assertEquals(CrashKind.ANR, DropBoxParser.kindOf("data_app_anr"))
        assertEquals(CrashKind.NATIVE, DropBoxParser.kindOf("data_app_native_crash"))
        assertEquals(CrashKind.SYSTEM, DropBoxParser.kindOf("SYSTEM_RESTART"))
        assertNull(DropBoxParser.kindOf("SYSTEM_TOMBSTONE"))
        assertNull(DropBoxParser.kindOf("netstats"))
    }

    @Test
    fun packageName() {
        assertEquals("com.foo.bar", DropBoxParser.packageOf("Process: com.foo.bar\nPID: 123\n"))
        assertEquals("com.x", DropBoxParser.packageOf("Flags: 0x0\nPackage: com.x v12 (1.2)\n"))
        assertNull(DropBoxParser.packageOf("nothing here"))
    }

    @Test
    fun summaries() {
        val crash = "Process: com.foo\nFlags: 0x0\n\njava.lang.IllegalStateException: boom\n\tat com.foo.Main.run(Main.kt:10)"
        assertEquals("java.lang.IllegalStateException: boom", DropBoxParser.summaryOf(crash))
        val anr = "Process: com.foo\nSubject: Input dispatching timed out\n\nmore"
        assertEquals("Input dispatching timed out", DropBoxParser.summaryOf(anr))
        val native = "Process: com.foo\n\n*** *** ***\nsignal 6 (SIGABRT), code -1\nAbort message: 'bad state'"
        assertEquals("'bad state'", DropBoxParser.summaryOf(native))
    }
}