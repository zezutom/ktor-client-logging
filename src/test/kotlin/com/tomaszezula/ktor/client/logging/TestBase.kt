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
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.http.*
import io.ktor.utils.io.*
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.test.assertEquals
import kotlin.test.assertFalse

abstract class TestBase {

    private lateinit var logger: Logger

    private lateinit var httpClient: HttpClient

    private lateinit var tracing: Tracing

    private val deserializedResponse = IpResponse("127.0.0.1")

    protected lateinit var memoryAppender: MemoryAppender

    @Before
    fun before() = runBlocking {
        httpClient = loggingHttpClient()
        logger = httpClient[ClientLogging].logger
        tracing = Tracing(TracingConfig())
        initLogging()
    }

    @After
    fun resetLogging() {
        memoryAppender.reset()
        memoryAppender.stop()
    }
    
    protected open fun init(client: HttpClient): HttpClient =
        client.config {
            install(ClientLogging)
        }
    
    protected fun verifyRequest(level: Level) = runBlocking {
        withRequest {
            TestCase.assertTrue(
                "Request should be logged!", memoryAppender.contains("Request GET", level)
            )
        }
    }

    protected fun verifyResponse(level: Level) = runBlocking {
        withRequest {
            TestCase.assertTrue(
                "Response should be logged!",
                memoryAppender.contains("Response from: GET", level)
            )
        }
    }

    private fun initLogging() {
        memoryAppender = MemoryAppender()
        memoryAppender.context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logbackLogger = logger as ch.qos.logback.classic.Logger
        logbackLogger.level = Level.TRACE
        logbackLogger.addAppender(memoryAppender)
        memoryAppender.start()
    }

    private fun loggingHttpClient(): HttpClient =
        httpClient().config {
            install(ClientLogging)
        }

    protected fun httpClient(): HttpClient {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""{"ip":"${deserializedResponse.ip}"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        return init(
            HttpClient(mockEngine) {
                install(JsonFeature) {
                    serializer = KotlinxSerializer()
                }
            }
        )
    }

    protected suspend fun withAuthHeader(traceId: TraceId? = null, block: () -> Unit) {
        withRequest(traceId, deserializedResponse, testClient(httpClient)::getIpWithAuth, block)
    }

    protected suspend fun withHeaders(
        headers: Map<String, String>,
        httpClient: HttpClient = this.httpClient,
        traceId: TraceId? = null,
        block: () -> Unit
    ) {
        withRequest(traceId, deserializedResponse, { testClient(httpClient).getIp(headers) }, block)
    }

    protected suspend fun withRequest(
        httpClient: HttpClient = this.httpClient,
        traceId: TraceId? = null,
        block: () -> Unit
    ) {
        withRequest(traceId, deserializedResponse, testClient(httpClient)::getIp, block)
    }

    private fun testClient(httpClient: HttpClient): TestClient = TestClient(httpClient)

    private suspend fun <T> withRequest(
        traceId: TraceId? = null,
        expectedResponse: T,
        call: suspend () -> T,
        block: () -> Unit
    ) {
        traceId?.let {
            tracing.withTraceId(it) {
                assertEquals(expectedResponse, call())
                block()
            }
            assertFalse(
                (MDC.getCopyOfContextMap() ?: emptyMap()).containsKey("traceId"),
                "MDC should be cleared after each request!"
            )
        } ?: run {
            assertEquals(expectedResponse, call())
            block()
        }
    }
}