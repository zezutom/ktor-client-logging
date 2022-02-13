package com.tomaszezula.ktor.client.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.tomaszezula.ktor.client.IpResponse
import com.tomaszezula.ktor.client.TestClient
import com.tomaszezula.ktor.client.tracing.TraceId
import com.tomaszezula.ktor.client.tracing.Tracing
import com.tomaszezula.ktor.client.tracing.TracingConfig
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.http.*
import io.ktor.utils.io.*
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ClientLoggingTest {

    private lateinit var logger: Logger

    private lateinit var memoryAppender: MemoryAppender

    private lateinit var client: TestClient

    private lateinit var tracing: Tracing

    private val deserializedResponse = IpResponse("127.0.0.1")

    @Before
    fun before() {
        logger = LoggerFactory.getLogger(HttpClient::class.java)
        client = TestClient(httpClient(logger))
        tracing = Tracing(TracingConfig())
        initLogging()
    }

    @After
    fun resetLogging() {
        memoryAppender.reset()
        memoryAppender.stop()
    }

    @Test
    fun logsRequestAndResponse() = runBlocking {
        withRequest {
            assertTrue(
                "Request should be logged!", memoryAppender.contains("Request GET", Level.DEBUG)
            )
            assertTrue(
                "Response should be logged!",
                memoryAppender.contains("Response from: GET", Level.DEBUG)
            )
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
    fun logsTraceId() = runBlocking {
        val traceId = TraceId.generate()
        withRequest(traceId) {
            memoryAppender.getAllEvents().forEach { event ->
                assertTrue(
                    "Trace ID should be part of MDC!",
                    event.mdcPropertyMap["traceId"] == traceId.value
                )
            }
        }
    }

    @Test
    fun traceIdIsUniquePerCall() = runBlocking {
        repeat((0 .. 10).count()) {
            val traceId = TraceId.generate()
            withRequest(traceId) {
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
    
    private suspend fun withRequest(traceId: TraceId? = null, block: () -> Unit) {
        traceId?.let {
            tracing.withTraceId(it) {
                assertEquals(deserializedResponse, client.getIp())
                block()
            }
            assertFalse(
                (MDC.getCopyOfContextMap() ?: emptyMap()).containsKey("traceId"),
                "MDC should be cleared after each request!"
            )
        } ?: run {
            assertEquals(deserializedResponse, client.getIp())
            block()
        }
    }

    private fun initLogging() {
        memoryAppender = MemoryAppender()
        memoryAppender.context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logbackLogger = logger as ch.qos.logback.classic.Logger
        logbackLogger.level = Level.DEBUG
        logbackLogger.addAppender(memoryAppender)
        memoryAppender.start()
    }

    private fun httpClient(logger: Logger): HttpClient {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""{"ip":"${deserializedResponse.ip}"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(mockEngine) {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
            install(ClientLogging) {
                this.logger = logger
            }
        }
    }
}