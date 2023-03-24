package com.github.gunnaringe.smschatbot

import com.sksamuel.hoplite.Masked

data class Config(
    val wg2: Wg2Config,
    val openai: OpenAIConfig,
    val phones: List<String>,
)

data class Wg2Config(
    val clientId: String,
    val clientSecret: Masked,

    val eventQueue: String = "smschatbot",
)

data class OpenAIConfig(
    val apiKey: Masked,
)
