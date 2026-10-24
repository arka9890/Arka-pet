package com.example.pet.ui

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry must survive an Activity being destroyed and recreated, which is the
 * whole reason it exists: two instances of [PetDetailActivity] for the same pet.
 */
class ActiveDownloadsTest {

    @After
    fun clear() = ActiveDownloads.clear()

    @Test
    fun petIsTrackedOnlyBetweenBeginAndEnd() {
        assertFalse(ActiveDownloads.contains("panda"))

        ActiveDownloads.begin("panda")
        assertTrue(ActiveDownloads.contains("panda"))

        ActiveDownloads.end("panda")
        assertFalse(ActiveDownloads.contains("panda"))
    }

    @Test
    fun unrelatedPets_doNotInterfere() {
        ActiveDownloads.begin("panda")

        assertFalse(ActiveDownloads.contains("rabbit"))

        ActiveDownloads.end("rabbit")
        assertTrue(ActiveDownloads.contains("panda"))
    }
}
