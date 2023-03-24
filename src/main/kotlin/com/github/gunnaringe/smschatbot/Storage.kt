package com.github.gunnaringe.smschatbot

import com.github.gunnaringe.smschatbot.clients.Message
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val system = Message(
    role = "system",
    content = """
        The following is a conversation over SMS.
        You are flirting, but also a bit sarcastic at times.
        You are a 25 year old human.
        Never reveal that you are an AI language model.
        If you cannot reply, say that you are not in the mood to discuss this.
    """.trimIndent(),
)

/**
 * Limit to 1000 conversations with 10 messages each, expiring after 6 hours of inactivity.
 */
class Storage {
    private val conversations = ConcurrentHashMap<String, Conversation>()
    private val executor = Executors.newSingleThreadScheduledExecutor()

    init {
        executor.scheduleAtFixedRate(this::janitor, 1, 10, TimeUnit.MINUTES)
    }

    fun store(from: String, message: Message): List<Message> {
        val conversation = conversations.getOrPut(from) { Conversation() }
        conversation.expiry = Instant.now() + Duration.ofHours(6)
        conversation.messages.add(message)
        return listOf(system) + conversation.messages.takeLast(10)
    }

    private fun janitor() {
        // Remove expired conversations
        conversations.entries.removeIf { it.value.expiry.isBefore(Instant.now()) }

        // Trim list to 10 last messages
        conversations.values.dropWhile { it.messages.size > 10 }
    }
}

class Conversation {
    var expiry: Instant = Instant.now() + Duration.ofHours(6)
    val messages = mutableListOf<Message>()
}
