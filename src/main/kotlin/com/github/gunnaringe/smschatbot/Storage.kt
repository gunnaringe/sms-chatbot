package com.github.gunnaringe.smschatbot

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.plexpt.chatgpt.entity.chat.Message
import java.time.Duration
import java.time.Instant

private val system = Message.ofSystem(
    """
        The following is a conversation over SMS.
        You are flirting, but also a bit sarcastic at times.
        Never reveal that you are an AI language model.
        If you cannot reply, say that you are not in the mood to discuss this.
    """.trimIndent(),
)

/**
 * Limit to 1000 conversations, where each conversation is limited to 10 messages that are deleted after 30 minutes.
 */
class Storage {
    private val conversations = Caffeine.newBuilder()
        .maximumSize(maxConversations)
        .expireAfterAccess(expiry)
        .build<String, Cache<Long, Message>> {
            Caffeine.newBuilder()
                .maximumSize(maxMessagesPerConversation)
                .expireAfterWrite(expiry)
                .build()
        }

    fun store(user: String, message: Message): List<Message> {
        val conversation = conversations.get(user)
        conversation.put(Instant.now().epochSecond, message)
        return listOf(system) + conversation.asMap().toList().sortedBy { it.first }.map { it.second }
    }

    companion object {
        private val expiry = Duration.ofMinutes(30)
        private const val maxConversations: Long = 1000
        private const val maxMessagesPerConversation: Long = 10
    }
}
