package com.tomaszezula.ktor.client

import com.tomaszezula.ktor.client.logging.ClientLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import java.util.*

class TestClient(private val httpClient: HttpClient) {
    
    companion object {
        private val EncodedCredentials =
            Base64.getEncoder().encodeToString("test:test".toByteArray(Charsets.UTF_8))
        
        fun newInstance(): TestClient {
            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel("""{"ip":"127.0.0.1"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            return TestClient(httpClient(mockEngine))
        }

        private fun httpClient(engine: HttpClientEngine) = HttpClient(engine) {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
            install(ClientLogging)
        }
    }

    suspend fun getIp(headers: Map<String, String> = emptyMap()): IpResponse =
        httpClient.get("https://api.ipify.org/?format=json") {
            headers {
                headers.forEach(this::append)
            }
        }

    suspend fun getIpWithAuth(): IpResponse =
        httpClient.get("https://api.ipify.org/?format=json") {
            header(HttpHeaders.Authorization, "Basic $EncodedCredentials")
        }

}

@Serializable
data class IpResponse(val ip: String)