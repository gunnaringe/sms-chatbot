package com.github.gunnaringe.smschatbot

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.caffeine.CaffeineProxyManager
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.distributed.remote.RemoteBucketState
import java.time.Duration

class Ratelimiter(private val limits: List<RatelimitConfig>) {

    private val caffeine: Caffeine<String, RemoteBucketState> = Caffeine.newBuilder()
        .maximumSize(10_000)
        as Caffeine<String, RemoteBucketState>
    private val proxyManager: ProxyManager<String> = CaffeineProxyManager(caffeine, Duration.ofDays(2))

    private val configuration = BucketConfiguration.builder()
        .apply { limits.forEach { addLimit(Bandwidth.simple(it.limit, it.duration)) } }
        .build()

    fun allow(from: String): Boolean {
        val bucket = proxyManager.builder().build(from, configuration)
        return bucket.tryConsume(1)
    }
}
