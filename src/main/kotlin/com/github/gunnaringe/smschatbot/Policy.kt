package com.github.gunnaringe.smschatbot

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.slf4j.LoggerFactory

class Policy(private val enabledRecipients: List<String>) {

    fun isAllowed(from: String, to: String): Boolean {
        if (to !in enabledRecipients) {
            logger.warn("[BLOCK] {} -> {}: Recipient is not enabled", from, to)
            return false
        }

        if (from.isEmpty()) {
            logger.warn("[BLOCK] {} -> {}: Sender is not a E.164 number", from, to)
            return false
        }

        if (to == from) {
            logger.warn("[BLOCK] {} -> {}: Sent to self", from, to)
            return false
        }

        val fromNumber = PhoneNumberUtil.getInstance().parse(from, "ZZ")
        val toNumber = PhoneNumberUtil.getInstance().parse(to, "ZZ")

        if (fromNumber.countryCode != toNumber.countryCode) {
            logger.warn("[BLOCK] {} -> {}: Sender and recipient are not in the same country", from, to)
            return false
        }

        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Policy::class.java)
    }
}
