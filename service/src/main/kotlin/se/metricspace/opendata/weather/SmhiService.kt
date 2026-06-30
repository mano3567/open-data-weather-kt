package se.metricspace.opendata.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.time.Instant

// Domänmodellen kan med fördel ligga utanför, men jag behåller din struktur!
data class WeatherForecast(
    val cloudCoverOctas: Int,
    val humidity: Double,
    val isClearSky: Boolean,
    val precipitationMm: Double,
    val temperature: Double,
    val time: Instant,
    val windSpeed: Double,
    val visibilityKm: Double,
)

class SmhiService(private val userAgent: String) {

    // 1. Initiera och konfigurera Ktor-klienten lokalt
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // Ignorerar all extra SMHI-data vi inte vill ha
            })
        }
    }

    private fun Double?.validSmhiValue(fallback: Double = 0.0): Double {
        return if (this == null || this == 9999.0) fallback else this
    }

    @Serializable private data class SmhiResponse(val timeSeries: List<TimeSeries>)
    @Serializable private data class TimeSeries(val time: String, val data: SmhiData)

    @Serializable
    private data class SmhiData(
        val cloud_area_fraction: Double? = null,
        val air_temperature: Double? = null,
        val wind_speed: Double? = null,
        val relative_humidity: Double? = null,
        val visibility_in_air: Double? = null,
        val precipitation_amount_mean: Double? = null
    )

    // 2. Använd den lokala klienten
    suspend fun getSmhiForecast(lat: Double, lon: Double): Result<List<WeatherForecast>> = runCatching {
        val latStr = String.format(Locale.US, "%.5f", lat)
        val lonStr = String.format(Locale.US, "%.5f", lon)

        val url = "https://opendata-download-metfcst.smhi.se/api/category/snow1g/version/1/geotype/point/lon/$lonStr/lat/$latStr/data.json"

        val smhiData: SmhiResponse = client.get(url) {
            header(HttpHeaders.UserAgent, userAgent)
        }.body()

        val now = ZonedDateTime.now()
        val zoneId = ZoneId.of("UTC")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        smhiData.timeSeries
            .take(120)
            .mapNotNull { timeStep ->
                val localTime = ZonedDateTime.parse(timeStep.time).withZoneSameInstant(zoneId)
                if (!localTime.isAfter(now)) return@mapNotNull null

                val data = timeStep.data
                val cloudCover = data.cloud_area_fraction.validSmhiValue(8.0).toInt()

                WeatherForecast(
                    time = localTime.toInstant(),
                    cloudCoverOctas = cloudCover,
                    isClearSky = cloudCover == 0,
                    temperature = data.air_temperature.validSmhiValue(),
                    windSpeed = data.wind_speed.validSmhiValue(),
                    humidity = data.relative_humidity.validSmhiValue(),
                    visibilityKm = data.visibility_in_air.validSmhiValue(),
                    precipitationMm = data.precipitation_amount_mean.validSmhiValue()
                )
            }
    }

    fun close() {
        client.close()
    }
}