package com.github.gunnaringe.smschatbot

import com.github.gunnaringe.smschatbot.clients.ChatGPT
import com.github.gunnaringe.smschatbot.clients.Message
import com.github.gunnaringe.smschatbot.clients.ReceiveSms
import com.github.gunnaringe.smschatbot.clients.SendSms
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
    val chatGPT = ChatGPT(config.openai.apiKey.value)

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

        val messages = storage.store(sms.from, Message(role = "user", content = sms.content))
        val response = chatGPT.generateResponse(messages)
        val botReply = response.choices.first().message

        val conversation = storage.store(sms.from, botReply)

        println(
            """
            | ${sms.from}:
            |   ${conversation.joinToString(separator = "\n") { "  - ${it.role} => ${it.content}" }}}
            |
            """.trimMargin(),
        )

        sendSmsClient.sendSms(sendFrom, sendTo, botReply.content.trim())
    }

    blockUntilInterrupted()

    channel.shutdown()
    channel.awaitTermination(10, TimeUnit.SECONDS)
}

private fun blockUntilInterrupted() = try {
    Thread.currentThread().join()
} catch (e: InterruptedException) {
}
