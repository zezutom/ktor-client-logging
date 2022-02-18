package com.tomaszezula.ktor.client.logging

import ch.qos.logback.classic.Level
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ClientLoggingLogLevelTest : TestBase() {

    @Test
    fun logsOnTraceLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                level { trace() }
            }            
        }) {
            verifyRequest(Level.TRACE)
            verifyResponse(Level.TRACE)            
        }
    }
    
    @Test
    fun logsOnDebugLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                level { debug() }
            }
        }) {
            verifyRequest(Level.DEBUG)
            verifyResponse(Level.DEBUG)
        }
    }

    @Test
    fun logsOnInfoLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                level { info() }
            }
        }) {
            verifyRequest(Level.INFO)
            verifyResponse(Level.INFO)
        }
    }
    
    @Test
    fun logsRequestOnTraceLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                req {
                    level { trace() }
                }
            }
        }) {
            verifyRequest(Level.TRACE)
            verifyResponse(Level.DEBUG)
        }
    }

    @Test
    fun logsRequestOnDebugLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                req {
                    level { debug() }
                }
            }
        }) {
            verifyRequest(Level.DEBUG)
            verifyResponse(Level.DEBUG)
        }
    }

    @Test
    fun logsRequestOnInfoLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                req {
                    level { info() }
                }
            }
        }) {
            verifyRequest(Level.INFO)
            verifyResponse(Level.DEBUG)
        }
    }


    @Test
    fun logsResponseOnTraceLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                res {
                    level { trace() }
                }
            }
        }) {
            verifyRequest(Level.DEBUG)
            verifyResponse(Level.TRACE)
        }
    }

    @Test
    fun logsResponseOnDebugLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                res {
                    level { debug() }
                }
            }
        }) {
            verifyRequest(Level.DEBUG)
            verifyResponse(Level.DEBUG)
        }
    }

    @Test
    fun logsResponseOnInfoLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                res {
                    level { info() }
                }
            }
        }) {
            verifyRequest(Level.DEBUG)
            verifyResponse(Level.INFO)
        }
    }
    
    @Test
    fun requestLevelOverridesGlobalLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                level { info() }
                req {
                    level { trace() }
                }
            }
        }) {
            verifyRequest(Level.TRACE)
            verifyResponse(Level.INFO)
        }
    }

    @Test
    fun responseLevelOverridesGlobalLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                level { info() }
                res {
                    level { trace() }
                }
            }
        }) {
            verifyRequest(Level.INFO)
            verifyResponse(Level.TRACE)
        }
    }
    
    @Test
    fun requestAndResponseLevelOverrideGlobalLevel() = runBlocking {
        withRequest(httpClient().config {
            install(ClientLogging) {
                level { debug() }
                req {
                    level { info() }
                }
                res {
                    level { trace() }
                }
            }
        }) {
            verifyRequest(Level.INFO)
            verifyResponse(Level.TRACE)
        }
    }
}