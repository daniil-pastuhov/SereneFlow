package com.sereneflow.timer

import com.sereneflow.timer.model.Routine
import com.sereneflow.timer.model.RoutineAccent
import com.sereneflow.timer.storage.StorageJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutineAccentTest {

    @Test
    fun accentsOlderVersionsStoredButNeverShowedFollowTheTheme() {
        val json = """[
            {"id":"a","title":"A","accentColorHex":${0xFF5B8A72L}},
            {"id":"b","title":"B","accentColorHex":${0xFF436B54L}},
            {"id":"c","title":"C","accentColorHex":${RoutineAccent.ROSE.argb}},
            {"id":"d","title":"D"}
        ]"""
        val routines = StorageJson.decodeFromString<List<Routine>>(json).map { it.migrated() }
        assertNull(routines[0].accentColorHex)
        assertNull(routines[1].accentColorHex)
        assertEquals(RoutineAccent.ROSE.argb, routines[2].accentColorHex)
        assertNull(routines[3].accentColorHex)
    }

    @Test
    fun chosenAccentRoundTrips() {
        val routine = Routine(id = "a", title = "A", accentColorHex = RoutineAccent.OCEAN.argb)
        val decoded = StorageJson.decodeFromString<Routine>(StorageJson.encodeToString(routine)).migrated()
        assertEquals(RoutineAccent.OCEAN, RoutineAccent.of(decoded.accentColorHex))
    }
}
