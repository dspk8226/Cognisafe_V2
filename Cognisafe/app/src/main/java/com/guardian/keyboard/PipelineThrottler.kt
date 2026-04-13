package com.guardian.keyboard

class PipelineThrottler(
    private val cooldownMs: Long = 20_000L
) {
    private var lastTriggeredAt: Long = 0L

    fun shouldTrigger(nowMs: Long = System.currentTimeMillis()): Boolean {
        return (nowMs - lastTriggeredAt) >= cooldownMs
    }

    fun markTriggered(nowMs: Long = System.currentTimeMillis()) {
        lastTriggeredAt = nowMs
    }
}

