package com.tomaszezula.ktor.client.logging

import com.tomaszezula.ktor.client.tracing.TraceId
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ClientLoggingTraceIdTest : TestBase() {
    
    @Test
    fun logsTraceId() = runBlocking {
        val traceId = TraceId.generate()
        withRequest(traceId = traceId) {
            memoryAppender.getAllEvents().forEach { event ->
                assertTrue(
                    "Trace ID should be part of MDC!",
                    event.mdcPropertyMap["traceId"] == traceId.value
                )
            }
        }
    }

    @Test
    fun reportsMissingTracing() = runBlocking {
        withRequest {
            assertTrue(
                "Missing tracing should be reported both for the request and the response!",
                memoryAppender.findEvents("TracingContext not found!").count() == 2
            )
        }
    }

    @Test
    fun traceIdIsUniquePerCall() = runBlocking {
        repeat((0 .. 10).count()) {
            val traceId = TraceId.generate()
            withRequest(traceId = traceId) {
                memoryAppender.getAllEvents().forEach { event ->
                    assertTrue(
                        "Trace ID should be part of MDC!",
                        event.mdcPropertyMap["traceId"] == traceId.value
                    )
                }
                memoryAppender.reset()
            }
        }
    }
}