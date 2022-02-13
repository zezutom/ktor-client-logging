package com.tomaszezula.ktor.client.logging

import com.tomaszezula.ktor.client.tracing.Tracing
import com.tomaszezula.ktor.client.tracing.TracingConfig
import com.tomaszezula.ktor.client.tracing.TracingContext
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import kotlin.coroutines.coroutineContext

class ClientLogging(
    private val logger: Logger,
    private val filters: List<(HttpRequestBuilder) -> Boolean>,
    tracingConfig: TracingConfig
) {

    private val tracing = Tracing(tracingConfig)

    class Config {
        var logger: Logger = LoggerFactory.getLogger(HttpClient::class.java)
        var tracingConfig: TracingConfig = TracingConfig()
        var filters: List<(HttpRequestBuilder) -> Boolean> = emptyList()
    }

    companion object : HttpClientFeature<Config, ClientLogging> {
        override val key: AttributeKey<ClientLogging> =
            AttributeKey(ClientLogging::class.simpleName!!)

        override fun install(feature: ClientLogging, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
                try {
                    if (feature.filters.isEmpty() || feature.filters.any { it(context) }) {
                        tryRun { 
                            feature.withTraceId {
                                feature.logRequest(context)
                            }
                        }
                        proceed()
                    }
                } catch (cause: Throwable) {
                    feature.logRequestException(context, cause)
                    throw cause
                }
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
                val (loggingContent, responseContent) = response.content.split(scope)
                val newClientCall = context.wrapWithContent(responseContent)
                val sideCall = context.wrapWithContent(loggingContent)
                tryRun {
                    feature.withTraceId {
                        feature.logResponse(sideCall.response)
                    }
                }
                proceedWith(newClientCall.response)
            }
        }

        override fun prepare(block: Config.() -> Unit): ClientLogging {
            val config = Config().apply(block)
            return ClientLogging(config.logger, config.filters, config.tracingConfig)
        }
            
        private suspend fun tryRun(block: suspend () -> Unit) {
            try {
                block()
            } catch (_: Throwable) {
                // Do nothing!
            }
        }
    }
    
    
    private suspend fun withTraceId(block: suspend () -> Unit) {
        return coroutineContext[TracingContext]?.let { tracingContext ->
            tracing.withTraceId(tracingContext.traceId) {
                block()
            }
        } ?: run {
            logger.warn("TracingContext not found!")
            block()
        }
    }

    private fun logRequest(request: HttpRequestBuilder) {
        val requestUrl = Url(request.url)
        val headers = extractHeaders(
            request.headers.build(),
            request.contentLength(),
            request.contentType()
        )
        val body = when (val body = request.body) {
            is TextContent -> String(body.bytes())
            else -> "[request body omitted]"
        }
        logger.debug("Request ${request.method.value} ${requestUrl}, headers: $headers, body: $body")
    }

    private suspend fun logResponse(response: HttpResponse) {
        val from = "${response.call.request.method.value} ${response.call.request.url}"
        val statusCode = "${response.status.value} ${response.status.description}"
        val headers = extractHeaders(
            response.headers,
            response.contentLength(),
            response.contentType()
        )
        val body = response.contentType()?.let { contentType ->
            val charset = contentType.charset() ?: Charsets.UTF_8
            response.content.tryReadText(charset) ?: "[response body omitted]"
        }
        logger.debug("Response from: $from, statusCode: $statusCode, headers: $headers${body?.let { ", body: $it" } ?: ""}")
    }

    private fun logRequestException(request: HttpRequestBuilder, cause: Throwable) {
        logger.error("Request ${Url(request.url)} failed!", cause)
    }

    private fun <T> extractHeader(key: String, value: T?): Set<Map.Entry<String, List<T>>> =
        (value?.let { mapOf(key to listOf(it)) } ?: emptyMap()).entries

    private fun extractHeaders(
        callHeaders: Headers,
        contentLength: Long?,
        contentType: ContentType?
    ): List<Map.Entry<String, List<Any>>> =
        callHeaders.entries()
            .plus(extractHeader(HttpHeaders.ContentLength, contentLength))
            .plus(extractHeader(HttpHeaders.ContentType, contentType))
            .toList()
            .sortedBy { it.key }

    private suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? = try {
        readRemaining().readText(charset = charset)
    } catch (cause: Throwable) {
        null
    }
}