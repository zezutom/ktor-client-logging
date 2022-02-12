package com.tomaszezula.ktor.client.tracing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.MDC

class Tracing(private val tracingConfig: TracingConfig) {
    
    companion object {
        val DefaultInstance: Tracing = Tracing(TracingConfig())
    }
    
    suspend fun <T>withTraceId(traceId: TraceId, block: suspend CoroutineScope.() -> T): T =
        try {
            MDC.put(tracingConfig.traceIdKey, traceId.value)
            withContext(MDCContext().plus(TracingContext(traceId)), block)
        } finally {
            MDC.clear()
        }
}