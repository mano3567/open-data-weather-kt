package se.metricspace.opendata.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.time.Instant

data class MetForecast(
    val airTemperature: Double? = null,
    val cloudAreaFraction: Double? = null,
    val precipitationAmount: Double? = null,
    val relativeHumidity: Double? = null,
    val time: Instant,
    val windSpeed: Double? = null,
)
class MetNorwayService(private val userAgent: String) {
    @Serializable
    data class MetNorwayResponse(val properties: MetProperties)

    @Serializable
    data class MetProperties(val timeseries: List<MetTimeSeries>)

    @Serializable
    data class MetTimeSeries(val time: String, val data: MetData)

    @Serializable
    data class MetData(
        val instant: MetInstant,
        val next_1_hours: MetPeriod? = null // Kan vara null långt in i framtiden!
    )

    @Serializable
    data class MetInstant(val details: MetDetails)

    @Serializable
    data class MetDetails(
        val air_temperature: Double? = null,
        val wind_speed: Double? = null,
        val cloud_area_fraction: Double? = null,
        val relative_humidity: Double? = null
        // Tog bort precipitation_amount härifrån eftersom den inte bor här
    )

    // Nya klasser för att fånga regnet!
    @Serializable
    data class MetPeriod(val details: MetPeriodDetails)

    @Serializable
    data class MetPeriodDetails(val precipitation_amount: Double? = null)


    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getForecast(latitude: Double, longitude: Double): Result<List<MetForecast>> = runCatching {
        val rawResponse = getRawForecast(latitude, longitude).getOrThrow()
        val now = ZonedDateTime.now()
        val zoneId = ZoneId.of("UTC")
        rawResponse.properties.timeseries.mapNotNull { timeSeries ->
            val localTime = ZonedDateTime.parse(timeSeries.time).withZoneSameInstant(zoneId)
            if (!localTime.isAfter(now)) return@mapNotNull null
            val details = timeSeries.data.instant.details
            val rainDetails = timeSeries.data.next_1_hours?.details // Hämta regnet!
            MetForecast(
                airTemperature = details.air_temperature,
                cloudAreaFraction = details.cloud_area_fraction,
                precipitationAmount = rainDetails?.precipitation_amount,
                relativeHumidity = details.relative_humidity,
                time = Instant.parse(timeSeries.time),
                windSpeed = details.wind_speed
            )
        }
    }

    suspend fun getRawForecast(latitude: Double, longitude: Double): Result<MetNorwayResponse> = runCatching {
        val latStr = String.format(Locale.US, "%.4f", latitude)
        val lonStr = String.format(Locale.US, "%.4f", longitude)

        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$latStr&lon=$lonStr"

        httpClient.get(url) {
            header(HttpHeaders.UserAgent, userAgent)
        }.body()
    }

    fun close() {
        httpClient.close()
    }
}