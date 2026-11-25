package io.github.lk024.orario.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RemotePauseTest {

    @Test
    fun oldScriptWithoutPauseIsUnsupported() {
        assertEquals(RemotePause.Unsupported, parseRemotePause("""{"substitutions":[]}"""))
    }

    @Test
    fun nullPauseMeansNone() {
        assertEquals(RemotePause.None, parseRemotePause("""{"substitutions":[],"pause":null}"""))
    }

    @Test
    fun periodIsRead() {
        assertEquals(
            RemotePause.Period(LocalDate.of(2026, 12, 23), LocalDate.of(2027, 1, 6)),
            parseRemotePause("""{"pause":{"from":"2026-12-23","to":"2027-01-06"}}"""),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun scriptErrorsAreReported() {
        parseRemotePause("""{"error":"unauthorized"}""")
    }
}
