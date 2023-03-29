package com.github.gunnaringe.smschatbot

import com.github.gunnaringe.smschatbot.clients.ReceiveSms
import com.github.gunnaringe.smschatbot.clients.SendSms
import com.plexpt.chatgpt.ChatGPT
import com.plexpt.chatgpt.entity.chat.ChatCompletion
import com.plexpt.chatgpt.entity.chat.Message
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource
import com.wgtwo.auth.WgtwoAuth
import io.grpc.ManagedChannelBuilder
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("com.github.gunnaringe.smschatbot.Main")
private val scope = setOf("sms.text:send_from_subscriber", "events.sms.subscribe")

fun main(args: Array<String>) {
    logger.info("Hello, SMS Chatbot!")

    val config = ConfigLoaderBuilder.default()
        .addEnvironmentSource(useUnderscoresAsSeparator = true, allowUppercaseNames = true)
        .apply { args.forEach { file -> addFileSource(file) } }
        .build()
        .loadConfigOrThrow<Config>()

    val wgtwoAuth = WgtwoAuth.builder(config.wg2.clientId, config.wg2.clientSecret.value).build()
    val tokenSource = wgtwoAuth.clientCredentials.newTokenSource(scope.joinToString(separator = " "))

    val storage = Storage()
    val policy = Policy(config.phones)
    val ratelimiter = Ratelimiter(config.ratelimit)

    val chatGPT = ChatGPT.builder()
        .apiKey(config.openai.apiKey.value)
        .build()
        .init()

    val channel = ManagedChannelBuilder.forAddress("api.wgtwo.com", 443)
        .useTransportSecurity()
        .keepAliveTime(30, TimeUnit.SECONDS)
        .keepAliveTimeout(10, TimeUnit.SECONDS)
        .keepAliveWithoutCalls(true)
        .build()

    val sendSmsClient = SendSms(channel, tokenSource)

    ReceiveSms(channel, tokenSource, config.wg2.eventQueue) { sms ->
        if (!policy.isAllowed(sms.from, sms.to)) {
            return@ReceiveSms
        }
        if (!ratelimiter.allow(sms.from)) {
            logger.warn("[BLOCK] {} -> {}: Hitting rate limit for sender", sms.from, sms.to)
            return@ReceiveSms
        }

        val sendTo = sms.from
        val sendFrom = sms.to

        val messages = storage.store(sms.from, Message("user", sms.content))

        val chatCompletion = ChatCompletion.builder()
            .model("gpt-3.5-turbo")
            .messages(messages)
            .maxTokens(1000)
            .build()

        val response = chatGPT.chatCompletion(chatCompletion)
        val responseMessage = response.choices.first().message
        val conversation = storage.store(sms.from, responseMessage)

        logger.info(
            "Spent {} tokens - prompt: {} completion: {}",
            response.usage.totalTokens,
            response.usage.promptTokens,
            response.usage.completionTokens,
        )
        print(sms.from, conversation)
        sendSmsClient.send(sendFrom, sendTo, responseMessage.content.trim())
    }

    blockUntilInterrupted()

    channel.shutdown()
    channel.awaitTermination(10, TimeUnit.SECONDS)
}

private fun blockUntilInterrupted() = try {
    Thread.currentThread().join()
} catch (e: InterruptedException) {
}

private fun print(user: String, messages: List<Message>) {
    val output = buildString {
        appendLine("Conversation with $user:")
        messages.forEach { message ->
            appendLine("${message.role}:")
            message.content.lines().forEach { line ->
                appendLine("  $line")
            }
        }
        appendLine("----")
    }
    print(output)
}
