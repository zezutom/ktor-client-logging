package com.tomaszezula.ktor.client

import com.tomaszezula.ktor.client.tracing.TraceId
import com.tomaszezula.ktor.client.tracing.Tracing
import kotlinx.coroutines.runBlocking

object TestApp {

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        // Initialize a Ktor client
        val client = TestClient.newInstance()
        
        // Initialize tracing with the default config, e.g. "traceId"
        val tracing = Tracing.DefaultInstance
        
        // Create a trace id for monitoring
        tracing.withTraceId(TraceId.generate()) {
            println(client.getIp())
        }

        // Do it again, with a different trace id
        tracing.withTraceId(TraceId.generate()) {
            println(client.getIp())
        }
    }
    
}