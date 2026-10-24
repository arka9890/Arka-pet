package com.example.pet.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@Ignore("Robolectric native runtime (SQLite) unsupported on aarch64 CI")
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
class ChatDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ChatDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertThenGetByPet_roundTrips() = kotlinx.coroutines.runBlocking {
        dao.insert(ChatMessage(petId = "panda", role = "user", text = "hi", timestamp = 1L))
        dao.insert(ChatMessage(petId = "panda", role = "assistant", text = "hello", timestamp = 2L))
        dao.insert(ChatMessage(petId = "rabbit", role = "user", text = "yo", timestamp = 3L))

        val panda = dao.getByPet("panda")
        assertEquals(2, panda.size)
        assertEquals("hi", panda[0].text)
        assertEquals("hello", panda[1].text)

        val rabbit = dao.getByPet("rabbit")
        assertEquals(1, rabbit.size)
        assertEquals("yo", rabbit[0].text)
    }

    @Test
    fun clear_removesOnlyThatPet() = kotlinx.coroutines.runBlocking {
        dao.insert(ChatMessage(petId = "panda", role = "user", text = "hi", timestamp = 1L))
        dao.insert(ChatMessage(petId = "rabbit", role = "user", text = "yo", timestamp = 2L))

        dao.clear("panda")

        assertTrue(dao.getByPet("panda").isEmpty())
        assertEquals(1, dao.getByPet("rabbit").size)
    }

    @Test
    fun clearAll_removesEverything() = kotlinx.coroutines.runBlocking {
        dao.insert(ChatMessage(petId = "panda", role = "user", text = "hi", timestamp = 1L))
        dao.clearAll()
        assertTrue(dao.getByPet("panda").isEmpty())
    }
}