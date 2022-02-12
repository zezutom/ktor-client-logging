package com.tomaszezula.ktor.client

import com.tomaszezula.ktor.client.logging.ClientLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.observer.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable

class TestClient(engine: HttpClientEngine) {
    
    companion object {
        fun newInstance(): TestClient {
            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel("""{"ip":"127.0.0.1"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            return TestClient(mockEngine)
        }
            
    }
    private val httpClient = HttpClient(engine) {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }
        install(ClientLogging)
    }

    suspend fun getIp(): IpResponse = 
        httpClient.get("https://api.ipify.org/?format=json")
}

@Serializable
data class IpResponse(val ip: String)