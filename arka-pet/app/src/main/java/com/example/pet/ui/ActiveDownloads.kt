package com.example.pet.ui

import java.util.Collections

/**
 * Process-level record of which pets are mid-download.
 *
 * Lives outside any Activity so rotating the screen or navigating between two detail
 * screens for the same pet cannot start a second concurrent install.
 */
object ActiveDownloads {

    private val ids: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun begin(petId: String) {
        ids.add(petId)
    }

    fun end(petId: String) {
        ids.remove(petId)
    }

    fun contains(petId: String): Boolean = ids.contains(petId)

    fun clear() = ids.clear()
}
