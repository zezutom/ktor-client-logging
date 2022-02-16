package com.tomaszezula.ktor.client.logging

import com.tomaszezula.ktor.client.logging.config.RequestConfig
import com.tomaszezula.ktor.client.logging.config.RequestConfig.AuthorizationConfig.Confidentiality
import com.tomaszezula.ktor.client.logging.config.ResponseConfig
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
import org.slf4j.event.Level
import java.nio.charset.Charset
import kotlin.coroutines.coroutineContext

class ClientLogging(
    private val logger: Logger,
    private val level: Level,
    private val requestConfig: RequestConfig,
    private val responseConfig: ResponseConfig,
    private val filters: List<(HttpRequestBuilder) -> Boolean>,
    tracingConfig: TracingConfig
) {

    private val tracing = Tracing(tracingConfig)

    private val isLoggingEnabled: Boolean =
        when (level) {
            Level.TRACE -> logger.isTraceEnabled
            Level.DEBUG -> logger.isDebugEnabled
            Level.INFO -> logger.isInfoEnabled
            else -> false   // WARN and ERROR are only for exception logging
        }

    class Config {
        var logger: Logger = LoggerFactory.getLogger(HttpClient::class.java)
        val levelBuilder: LevelBuilder = LevelBuilder()
        val requestBuilder: RequestBuilder = RequestBuilder()
        val responseBuilder: ResponseBuilder = ResponseBuilder()

        var tracingConfig: TracingConfig = TracingConfig()
        var filters: List<(HttpRequestBuilder) -> Boolean> = emptyList()

        fun level(init: LevelBuilder.() -> Unit): LevelBuilder = levelBuilder.apply(init)
        fun req(init: RequestBuilder.() -> Unit): RequestBuilder = RequestBuilder().apply(init)
        fun res(init: ResponseBuilder.() -> Unit): ResponseBuilder = ResponseBuilder().apply(init)

        class LevelBuilder() {
            var level: Level = Level.DEBUG

            fun trace() {
                level = Level.TRACE
            }

            fun debug() {
                level = Level.DEBUG
            }

            fun info() {
                level = Level.INFO
            }
        }

        class RequestBuilder() {
            val levelBuilder: LevelBuilder = LevelBuilder()
            val authorizationBuilder: AuthorizationBuilder = AuthorizationBuilder()
            var excludedHeaders: Set<String> = emptySet()
            var loggingEnabled: Boolean = true

            fun level(init: LevelBuilder.() -> Unit): LevelBuilder = levelBuilder.apply(init)
            fun excludeHeaders(vararg headers: String) {
                excludedHeaders = headers.toSet()
            }

            fun off() {
                loggingEnabled = false
            }

            fun authorization(init: AuthorizationBuilder.() -> Unit): AuthorizationBuilder =
                authorizationBuilder.apply(init)

            class AuthorizationBuilder() {
                var obfuscatePassword: Boolean = false
                var usernameOnly: Boolean = false
                var disabled: Boolean = true

                fun obfuscatePassword() {
                    obfuscatePassword = true
                }

                fun usernameOnly() {
                    usernameOnly = true
                }

                fun off() {
                    disabled = true
                }
            }
        }

        class ResponseBuilder() {
            val levelBuilder: LevelBuilder = LevelBuilder()
            var loggingEnabled: Boolean = true

            fun level(init: LevelBuilder.() -> Unit): LevelBuilder = levelBuilder.apply(init)
            fun off() {
                loggingEnabled = false
            }
        }
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
                if (feature.isLoggingEnabled) {
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
        }

        override fun prepare(block: Config.() -> Unit): ClientLogging {
            val config = Config().apply(block)
            val authorizationBuilder = config.requestBuilder.authorizationBuilder
            return ClientLogging(
                config.logger,
                config.levelBuilder.level,
                RequestConfig(
                    config.requestBuilder.levelBuilder.level,
                    RequestConfig.AuthorizationConfig(
                        if (authorizationBuilder.obfuscatePassword) Confidentiality.OBFUSCATE_PASSWORD
                        else if (authorizationBuilder.usernameOnly) Confidentiality.USERNAME_ONLY
                        else Confidentiality.EXCLUDE
                    ),
                    config.requestBuilder.excludedHeaders,
                    config.requestBuilder.loggingEnabled
                ),
                ResponseConfig(
                    config.responseBuilder.levelBuilder.level,
                    config.responseBuilder.loggingEnabled
                ),
                config.filters,
                config.tracingConfig
            )
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
        val headers = extractHeaders(request.headers.build())
        val body = when (val body = request.body) {
            is TextContent -> String(body.bytes())
            else -> "[request body omitted]"
        }
        logger.log("Request ${request.method.value} ${requestUrl}, headers: $headers, body: $body")
    }

    private suspend fun logResponse(response: HttpResponse) {
        val from = "${response.call.request.method.value} ${response.call.request.url}"
        val statusCode = "${response.status.value} ${response.status.description}"
        val headers = extractHeaders(response.headers)
        val body = response.contentType()?.let { contentType ->
            val charset = contentType.charset() ?: Charsets.UTF_8
            response.content.tryReadText(charset) ?: "[response body omitted]"
        }
        logger.log("Response from: $from, statusCode: $statusCode, headers: $headers${body?.let { ", body: $it" } ?: ""}")
    }

    private fun logRequestException(request: HttpRequestBuilder, cause: Throwable) {
        logger.error("Request ${Url(request.url)} failed!", cause)
    }

    private fun extractHeaders(headers: Headers): List<Map.Entry<String, List<Any>>> =
        headers.entries()
            .toList()
            .sortedBy { it.key }

    private suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? = try {
        readRemaining().readText(charset = charset)
    } catch (cause: Throwable) {
        null
    }

    private fun Logger.log(message: String) {
        when (level) {
            Level.TRACE -> log(message, logger::trace)
            Level.DEBUG -> log(message, logger::debug)
            Level.INFO -> log(message, logger::info)
            else -> {
                // Do nothing 
            }
        }
    }

    private fun log(message: String, logHandler: (String) -> Unit) {
        logHandler(message)
    }
}