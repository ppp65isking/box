package com.ppp65isking.box.data

import com.ppp65isking.box.model.WeatherNow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Open-Meteo — free, no API key, no account. Woburn, MA.
 *  Callers must run this on Dispatchers.IO. */
class WeatherClient(private val okHttp: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data class Ok(val weather: WeatherNow) : Result
        data class Error(val message: String) : Result
    }

    fun current(): Result {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=42.48&longitude=-71.15" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
        val request = Request.Builder().url(url).build()
        return runCatching {
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.Error("Weather service answered ${response.code}")
                }
                val current = json.parseToJsonElement(
                    response.body?.string().orEmpty(),
                ).jsonObject["current"]?.jsonObject
                    ?: return Result.Error("Weather response had no current conditions")
                Result.Ok(
                    WeatherNow(
                        tempC = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        humidity = current["relative_humidity_2m"]?.jsonPrimitive?.intOrNull ?: 0,
                        windKph = current["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0,
                        isDay = current["is_day"]?.jsonPrimitive?.contentOrNull == "1" ||
                            current["is_day"]?.jsonPrimitive?.booleanOrNull == true,
                    ),
                )
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }
}
