package com.tomaszezula.ktor.client.logging

import java.util.*
import java.util.regex.Pattern

sealed interface Printer<T> {
    fun print(value: T): String?
}

sealed interface AuthorizationHeaderPrinter : Printer<List<String>> {
    companion object {
        private const val EmptyString = ""
        private const val Delimiter = ":"
        private const val CredsSize = 2
        private val EncodedCredsPattern = Pattern.compile("(?i)Basic ", Pattern.UNICODE_CASE)
    }

    fun decodeCredentials(value: List<String>): Credentials? =
        try {
            value.firstOrNull()?.let { encodedCredentials ->
                val decodedCredentials = Base64.getDecoder().decode(
                    EncodedCredsPattern
                        .matcher(encodedCredentials)
                        .replaceAll(EmptyString)
                ).decodeToString()
                val credentials = decodedCredentials.split(Delimiter, limit = CredsSize)
                if (credentials.size == CredsSize) {
                    Credentials(credentials[0], credentials[1])
                } else null
            }
        } catch (cause: Throwable) {
            // Ignore
            null
        }

    data class Credentials(val username: String, val password: String)
}

object UsernameOnlyPrinter : AuthorizationHeaderPrinter {
    override fun print(value: List<String>): String? =
        decodeCredentials(value)?.username
}

object ObfuscatedPasswordPrinter : AuthorizationHeaderPrinter {

    private const val Asterisk = "*"
    private const val AnyCharacter = "."
    
    override fun print(value: List<String>): String? =
        decodeCredentials(value)?.let { credentials ->
            val password = credentials.password
            val obfuscatedPassword = if (password.length < 2) {
                password.replaceRange(0, password.length - 1, Asterisk)
            } else {
                "${password.first()}${
                    password
                        .substring(0, password.length - 2)
                        .replace(AnyCharacter.toRegex(), Asterisk)
                }${password.last()}"
            }
            "${credentials.username},$obfuscatedPassword"
        }

}

