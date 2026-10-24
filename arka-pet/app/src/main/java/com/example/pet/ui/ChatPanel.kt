package com.example.pet.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.pet.ai.OpenAiCompatibleClient
import com.example.pet.data.ChatDao
import com.example.pet.data.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatPanel(
    context: Context,
    private val client: OpenAiCompatibleClient,
    private val dao: ChatDao,
    private val petId: String,
) : LinearLayout(context) {

    private val messagesView = TextView(context).apply {
        textSize = 14f
        setPadding(16, 16, 16, 16)
    }
    private val input = EditText(context).apply {
        hint = "Say something…"
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
    }
    private val sendButton = Button(context).apply { text = "Send" }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        orientation = VERTICAL
        setBackgroundColor(0xCC222222.toInt())
        setPadding(16, 16, 16, 16)
        visibility = GONE

        val scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            addView(messagesView)
        }
        addView(scroll)

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(input)
            addView(sendButton)
        }
        addView(row)

        sendButton.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            input.setText("")
            send(text)
        }
    }

    fun show() {
        visibility = VISIBLE
        scope.launch { loadHistory() }
    }

    fun hide() {
        visibility = GONE
    }

    fun send(text: String) {
        append("You: $text")
        scope.launch {
            dao.insert(ChatMessage(petId = petId, role = "user", text = text, timestamp = System.currentTimeMillis()))
            val reply = withContext(Dispatchers.IO) { client.chat(listOf(com.example.pet.ai.AiMessage("user", text))) }
            reply.onSuccess { r ->
                append("Pet: $r")
                scope.launch { dao.insert(ChatMessage(petId = petId, role = "assistant", text = r, timestamp = System.currentTimeMillis())) }
            }.onFailure { e ->
                append("Pet: [error] ${e.message}")
            }
        }
    }

    private suspend fun loadHistory() {
        val history = withContext(Dispatchers.IO) { dao.getByPet(petId) }
        messagesView.text = history.joinToString("\n") { "${it.role}: ${it.text}" }
    }

    private fun append(line: String) {
        messagesView.append(if (messagesView.text.isEmpty()) line else "\n$line")
    }
}