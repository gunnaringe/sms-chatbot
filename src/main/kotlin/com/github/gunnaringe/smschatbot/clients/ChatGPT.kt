package com.github.gunnaringe.smschatbot.clients

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class Request(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int,
    val temperature: Double,
)

data class Response(
    val usage: ChatGPTUsage,
    val choices: List<ChatGPTChoice>,
)

data class ChatGPTUsage(
    val total_tokens: Int,
)

data class ChatGPTChoice(
    val message: Message,
)

data class Message(
    val role: String,
    val content: String,
)

private val objectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

class ChatGPT(private val apikey: String) {
    fun generateResponse(messages: List<Message>): Response {
        val client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build()

        val body = Request(
            model = "gpt-3.5-turbo",
            messages = messages,
            max_tokens = 100,
            temperature = 1.0,
        )

        val requestBody = objectMapper.writeValueAsString(body)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
            .header("Authorization", "Bearer $apikey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        logger.info("Sending request...")
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        logger.info("Got response: ${response.statusCode()}")
        return objectMapper.readValue(response.body())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ChatGPT::class.java)
    }
}
