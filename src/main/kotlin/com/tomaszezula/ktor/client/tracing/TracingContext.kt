package com.tomaszezula.ktor.client.tracing

import kotlin.coroutines.CoroutineContext

data class TracingContext(val traceId: TraceId) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<TracingContext> = Key

    companion object Key : CoroutineContext.Key<TracingContext>
}