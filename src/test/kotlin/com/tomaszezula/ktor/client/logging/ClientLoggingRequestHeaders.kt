package com.tomaszezula.ktor.client.logging

import ch.qos.logback.classic.Level
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ClientLoggingRequestHeaders : TestBase() {

    @Test
    fun excludesAuthorizationHeaderByDefault() = runBlocking {
        withAuthHeader {
            assertTrue(
                "Authorization header should not be logged!",
                memoryAppender.findEvents("Authorization", Level.DEBUG).isEmpty()
            )
        }
    }
    
    @Test
    fun excludesAuthorizationHeaderExplicitly() = runBlocking {
        withAuthHeader(httpClient = httpClient().config { 
            install(ClientLogging) {
                req {
                    authorization { 
                        off()
                    }
                }
            }
        }) {
            assertTrue(
                "Authorization header should not be logged!",
                memoryAppender.findEvents("Authorization", Level.DEBUG).isEmpty()
            )
        }
    }

    @Test
    fun removesPasswordFromAuthorizationHeader() = runBlocking { 
        withAuthHeader(httpClient = httpClient().config { 
            install(ClientLogging) {
                req {
                    authorization { 
                        usernameOnly()
                    }
                }
            }
        }) {
            assertTrue(
                "Authorization header containing username 'user' should be logged!",
                memoryAppender.findEvents("Authorization=[user]", Level.DEBUG).isNotEmpty()
            )
        }
    }

    @Test
    fun obfuscatesPasswordInAuthorizationHeader() = runBlocking {
        withAuthHeader(httpClient = httpClient().config {
            install(ClientLogging) {
                req {
                    authorization {
                        obfuscatePassword()
                    }
                }
            }
        }) {
            assertTrue(
                "Authorization header containing username 'user' and obfuscated password 'password' should be logged!",
                memoryAppender.findEvents("Authorization=[user,p******d]", Level.DEBUG).isNotEmpty()
            )
        }
    }
    
    @Test
    fun excludesHeaders() = runBlocking {
        val headers = mapOf(
            "X-Sensitive-Header-1" to "TEST",
            "X-Sensitive-Header-2" to "TEST",
            "X-Custom-Header-3" to "TEST"
        )
        withHeaders(headers, httpClient().config { 
            install(ClientLogging) {
                req {
                    excludeHeaders("X-Sensitive-Header-1", "X-Sensitive-Header-2")
                }
            }
        }) {
            setOf("X-Sensitive-Header-1", "X-Sensitive-Header-2").forEach { headerName ->
                assertTrue(
                    "$headerName should not be logged!",
                    memoryAppender.findEvents(headerName, Level.DEBUG).isEmpty()
                )
            }
            setOf("X-Custom-Header-3").forEach { headerName -> 
                assertTrue(
                    "$headerName should be logged!",
                    memoryAppender.findEvents(headerName, Level.DEBUG).isNotEmpty()
                )
            }
        }
    }
    
}