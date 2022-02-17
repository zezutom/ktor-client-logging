package com.tomaszezula.ktor.client.logging

import ch.qos.logback.classic.Level
import com.tomaszezula.ktor.client.tracing.TraceId
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ClientLoggingBasicTest : TestBase() {
    
    @Test
    fun logsRequestOnDebugLevel() = runBlocking {
        withRequest { 
            verifyRequest(Level.DEBUG)
        }
    }

    @Test
    fun logsResponseOnDebugLevel() = runBlocking {
        verifyResponse(Level.DEBUG)
    }

    @Test
    fun excludesAuthorizationHeader() = runBlocking {
        withAuthHeader {
            assertTrue(
                "Authorization header should not be logged!",
                memoryAppender.findEvents("Authorization", Level.DEBUG).isEmpty()
            )
        }
    }

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
}