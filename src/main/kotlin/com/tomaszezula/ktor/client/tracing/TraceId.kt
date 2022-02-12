package com.tomaszezula.ktor.client.tracing

import java.util.concurrent.ThreadLocalRandom

@JvmInline
value class TraceId(val value: String) {
    companion object {
        private val AllowedChars = ('a'..'z').plus(('0'..'9'))
        private const val Length = 10
        fun generate(): TraceId {
            val rnd = ThreadLocalRandom.current()
            val value = String(
                (0..Length)
                    .map { AllowedChars[rnd.nextInt(AllowedChars.size)] }
                    .toCharArray())
            return TraceId(value)
        }
    }
}