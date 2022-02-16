package com.tomaszezula.ktor.client.logging.config

import org.slf4j.event.Level

data class RequestConfig(
    val level: Level,
    val authorization: AuthorizationConfig,
    val excludedHeaders: Set<String>,
    val loggingEnabled: Boolean
) {
    data class AuthorizationConfig(val confidentiality: Confidentiality) {
        enum class Confidentiality {
            OBFUSCATE_PASSWORD, USERNAME_ONLY, EXCLUDE
        }
    }
}
