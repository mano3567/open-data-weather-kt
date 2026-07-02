package se.metricspace.opendata.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.time.Instant

data class WeatherForecastBySmhi(
    val cloudCoverOctas: Int,
    val humidity: Double,
    val precipitationMm: Double,
    val temperature: Double,
    val time: Instant,
    val windSpeed: Double,
    val visibilityKm: Double
)

class SmhiService(private val httpClient: HttpClient, private val userAgent: String) {
    private fun Double?.validSmhiValue(fallback: Double = 0.0): Double {
        return if (this == null || this == 9999.0) fallback else this
    }

    @Serializable private data class SmhiResponse(val timeSeries: List<TimeSeries>)
    @Serializable private data class TimeSeries(val time: String, val data: SmhiData)

    @Serializable
    private data class SmhiData(
        val air_temperature: Double? = null,
        val cloud_area_fraction: Double? = null,
        val precipitation_amount_mean: Double? = null,
        val relative_humidity: Double? = null,
        val visibility_in_air: Double? = null,
        val wind_speed: Double? = null
    )

    // 2. Använd den lokala klienten
    suspend fun getSmhiForecast(latitude: Double, longitude: Double): Result<List<WeatherForecastBySmhi>> = runCatching {
        val latStr = String.format(Locale.US, "%.5f", latitude)
        val lonStr = String.format(Locale.US, "%.5f", longitude)

        val url = "https://opendata-download-metfcst.smhi.se/api/category/snow1g/version/1/geotype/point/lon/$lonStr/lat/$latStr/data.json"

        val smhiData: SmhiResponse = httpClient.get(url) {
            header(HttpHeaders.UserAgent, userAgent)
        }.body()

        val now = ZonedDateTime.now()
        val zoneId = ZoneId.of("UTC")

        smhiData.timeSeries
            .take(120)
            .mapNotNull { timeStep ->
                val localTime = ZonedDateTime.parse(timeStep.time).withZoneSameInstant(zoneId)
                if (!localTime.isAfter(now)) return@mapNotNull null

                val data = timeStep.data
                val cloudCover = data.cloud_area_fraction.validSmhiValue(8.0).toInt()

                WeatherForecastBySmhi(
                    time = localTime.toInstant(),
                    cloudCoverOctas = cloudCover,
                    temperature = data.air_temperature.validSmhiValue(),
                    windSpeed = data.wind_speed.validSmhiValue(),
                    humidity = data.relative_humidity.validSmhiValue(),
                    visibilityKm = data.visibility_in_air.validSmhiValue(),
                    precipitationMm = data.precipitation_amount_mean.validSmhiValue()
                )
            }
    }
}