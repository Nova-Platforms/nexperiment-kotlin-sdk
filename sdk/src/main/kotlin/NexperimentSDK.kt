import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class Config<T>(val objectId: String, val appliedRuleId: String, val value: T)

data class Toggle(val objectId: String, val appliedRuleId: String, val value: Boolean)

class NexperimentSDK() {

    var baseUrl: String? = null
        private set

    var context: Map<String, Any> = mapOf()
        private set

    var token: String? = null
        private set

    suspend fun init(baseUrl: String, apiKey: String, apiSecret: String) {
        this.baseUrl = baseUrl

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("${this.baseUrl}/v1/client/auth")
            .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("x-api-key", apiKey)
            .addHeader("x-api-secret", apiSecret)
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (response.isSuccessful) {
            val responseData = response.body?.string()
            val json = Json.parseToJsonElement(responseData ?: "").jsonObject

            token = json["token"]?.jsonPrimitive?.contentOrNull;
        } else {
            throw IOException("Failed to fetch feature toggle")
        }
    }

    fun setContext(newContext: Map<String, Any>) {
        context = newContext
    }

    suspend fun getToggle(key: String): Toggle {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("$baseUrl/v1/client/feature-toggle/$key")
                .post("{\"context\": $context}".toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .addHeader("authorization", "bearer $token")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (response.isSuccessful) {
                val responseData = response.body?.string()
                val jsonObject = Json.parseToJsonElement(responseData!!)
                val objectId = jsonObject.jsonObject["objectId"]?.jsonPrimitive?.contentOrNull ?: ""
                val appliedRuleId = jsonObject.jsonObject["appliedRuleId"]?.jsonPrimitive?.contentOrNull ?: ""
                val value = jsonObject.jsonObject["value"]?.jsonPrimitive?.booleanOrNull ?: false

                return Toggle(objectId, appliedRuleId, value)
            } else {
                throw IOException("Failed to fetch feature toggle")
            }
        } catch (e: IOException) {
            throw IOException("Failed to fetch feature toggle: $e")
        }
    }

    suspend inline fun <reified T> getConfig(key: String): Config<T> {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("$baseUrl/v1/client/remote-config/$key")
                .post("{\"context\": $context}".toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .addHeader("authorization", "bearer $token")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (response.isSuccessful) {
                val responseData = response.body?.string()
                val jsonObject = Json.parseToJsonElement(responseData!!).jsonObject
                val objectId = jsonObject["objectId"]?.jsonPrimitive?.contentOrNull ?: ""
                val appliedRuleId = jsonObject["appliedRuleId"]?.jsonPrimitive?.contentOrNull ?: ""

                val valueJson = jsonObject["value"]?.jsonPrimitive?.contentOrNull ?: ""
                val value = Json.decodeFromString<T>(valueJson)

                return Config(objectId, appliedRuleId, value)
            } else {
                throw IOException("Failed to fetch remote config")
            }
        } catch (e: IOException) {
            throw IOException("Failed to fetch remote config: $e")
        }
    }
}