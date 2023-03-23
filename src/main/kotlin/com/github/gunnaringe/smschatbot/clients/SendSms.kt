package com.github.gunnaringe.smschatbot.clients

import com.wgtwo.api.v1.sms.SmsProto
import com.wgtwo.api.v1.sms.SmsServiceGrpc
import com.wgtwo.auth.ClientCredentialSource
import io.grpc.ManagedChannel
import org.slf4j.LoggerFactory

class SendSms(private val channel: ManagedChannel, private val tokenSource: ClientCredentialSource) {

    fun sendSms(from: String, to: String, content: String) {
        val stub = SmsServiceGrpc.newBlockingStub(channel)
            .withCallCredentials(tokenSource.callCredentials())

        val request = SmsProto.SendTextFromSubscriberRequest.newBuilder()
            .setFromSubscriber(from)
            .setToAddress(to)
            .setContent(content)
            .build()
        val response = stub.sendTextFromSubscriber(request)
        if (response.status == SmsProto.SendMessageResponse.SendStatus.SEND_STATUS_OK) {
            logger.info("SMS sent: from=${request.fromSubscriber} => to=${request.toAddress}")
        } else {
            logger.warn("SMS failed: from=${request.fromSubscriber} to=${request.toAddress} id=${response.messageId} Status=${response.status} description=${response.description}")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SendSms::class.java)
    }
}
