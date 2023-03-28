package com.github.gunnaringe.smschatbot.clients

import com.google.protobuf.duration
import com.wgtwo.api.v0.events.EventsProto
import com.wgtwo.api.v0.events.EventsServiceGrpc
import com.wgtwo.auth.ClientCredentialSource
import io.grpc.ManagedChannel
import io.grpc.StatusRuntimeException
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

data class Sms(
    val from: String,
    val to: String,
    val content: String,
)

class ReceiveSms(
    private val channel: ManagedChannel,
    private val tokenSource: ClientCredentialSource,
    queueName: String,
    private val callback: (sms: Sms) -> Unit,
) {
    private val executor = Executors.newFixedThreadPool(10)

    private val request = EventsProto.SubscribeEventsRequest.newBuilder()
        .setQueueName(queueName)
        .setDurableName(queueName)
        .setMaxInFlight(10)
        .addType(EventsProto.EventType.SMS_EVENT)
        .setManualAck(
            EventsProto.ManualAckConfig.newBuilder()
                .setEnable(true)
                .setTimeout(duration { seconds = 30 })
                .build(),
        )
        .build()

    init {
        while (!channel.isShutdown) {
            try {
                subscribe()
            } catch (e: StatusRuntimeException) {
                logger.error("Error while receiving SMS - Reconnect in 10 seconds", e)
                Thread.sleep(10_000)
            }
        }
    }

    private fun subscribe() {
        logger.info("Subscribing to SMS events...")
        EventsServiceGrpc.newBlockingStub(channel)
            .withCallCredentials(tokenSource.callCredentials())
            .subscribe(request)
            .forEach {
                executor.submit {
                    val ackRequest = EventsProto.AckRequest.newBuilder()
                        .setInbox(it.event.metadata.ackInbox)
                        .setSequence(it.event.metadata.sequence)
                        .build()
                    EventsServiceGrpc.newBlockingStub(channel)
                        .withCallCredentials(tokenSource.callCredentials())
                        .ack(ackRequest)

                    if (it.event.smsEvent.direction != EventsProto.SmsEvent.Direction.TO_SUBSCRIBER) {
                        logger.debug("Ignoring outbound SMS")
                        return@submit
                    }

                    val sms = Sms(
                        from = it.event.smsEvent.fromE164.e164,
                        to = it.event.smsEvent.toE164.e164,
                        content = it.event.smsEvent.text,
                    )

                    callback(sms)
                }
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReceiveSms::class.java)
    }
}
