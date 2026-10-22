package io.github.lk024.orario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class SubstitutionsSyncTest {

    private val thursday = LocalDate.of(2026, 10, 22)

    @Test
    fun remoteRowsUseOneBasedHoursAndLiberaIsFree() {
        assertEquals(Override("2026-10-22", 1, Lesson("3B", "Lab 2")), RemoteSubstitution("2026-10-22", 2, "3B", "Lab 2").toOverride())
        assertEquals(Override("2026-10-23", 3, null), RemoteSubstitution("2026-10-23", 4).toOverride())
    }

    @Test
    fun pendingChangesWinOverTheSheet() {
        val remote = listOf(
            Override.of(thursday, 1, Lesson("3B")),
            Override.of(thursday, 3, Lesson("5D")),
        )
        val pending = listOf(
            PendingChange(thursday.toString(), 1, Lesson("2A", "1.05")),
            PendingChange(thursday.toString(), 3, delete = true),
            PendingChange(thursday.toString(), 5, null),
        )
        val merged = mergeWithPending(remote, pending)

        assertEquals(Lesson("2A", "1.05"), merged.getValue(OverrideKey(thursday, 1)).lesson)
        assertFalse(OverrideKey(thursday, 3) in merged)
        assertEquals(null, merged.getValue(OverrideKey(thursday, 5)).lesson)
    }
}
