package com.tomaszezula.ktor.client.logging

import ch.qos.logback.classic.Level
import com.tomaszezula.ktor.client.tracing.TraceId
import junit.framework.TestCase.assertFalse
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
    fun disablesRequestLogging() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                req {
                    off()
                }
            }
        }) {
            assertFalse(
                "Request should not be logged!",
                memoryAppender.contains("Request GET", Level.DEBUG)
            )
            verifyResponse(Level.DEBUG)
        }
    }

    @Test
    fun disablesResponseLogging() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                res {
                    off()
                }
            }
        }) {
            assertFalse(
                "Response should not be logged!",
                memoryAppender.contains("Response GET", Level.DEBUG)
            )
            verifyResponse(Level.DEBUG)
        }
    }
}