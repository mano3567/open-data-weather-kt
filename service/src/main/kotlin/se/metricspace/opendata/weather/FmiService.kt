package se.metricspace.opendata.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

data class FmiForecast(
    val temperature: Double? = null,
    val windSpeed: Double? = null,
    val humidity: Double? = null,
    val totalCloudCover: Double? = null,
    val precipitation1h: Double? = null,
    val time: Instant
)

class FmiService(private val httpClient: HttpClient, private val userAgent: String) {
    private data class FmiDataPoint(val time: String, val param: String, val value: String)

    suspend fun getForecast(latitude: Double, longitude: Double): Result<List<FmiForecast>> = runCatching {
        // 1. Hämta den råa XML-texten
        val rawXml = getRawForecast(latitude, longitude).getOrThrow()

        // 2. Extrahera värdena med Regex
        val dataPoints = mutableListOf<FmiDataPoint>()
        val timeRegex = "<BsWfs:Time>(.*?)</BsWfs:Time>".toRegex()
        val nameRegex = "<BsWfs:ParameterName>(.*?)</BsWfs:ParameterName>".toRegex()
        val valRegex = "<BsWfs:ParameterValue>(.*?)</BsWfs:ParameterValue>".toRegex()

        // Vi delar upp den enorma texten i mindre block (ett för varje mätvärde)
        rawXml.split("<BsWfs:BsWfsElement").forEach { block ->
            val time = timeRegex.find(block)?.groupValues?.get(1)
            val name = nameRegex.find(block)?.groupValues?.get(1)
            val value = valRegex.find(block)?.groupValues?.get(1)

            if (time != null && name != null && value != null) {
                dataPoints.add(FmiDataPoint(time, name, value))
            }
        }

        // 3. Gruppera vår nya, rena lista på samma sätt som vi tänkte med JSON
        val groupedFeatures = dataPoints.groupBy { it.time }

        val now = ZonedDateTime.now()
        val zoneId = ZoneId.of("UTC")

        // 4. Bygg FmiForecast-objekten
        groupedFeatures.mapNotNull { (timeStr, points) ->
            val localTime = ZonedDateTime.parse(timeStr).withZoneSameInstant(zoneId)
            if (!localTime.isAfter(now)) return@mapNotNull null

            fun getValue(paramName: String): Double? {
                val strValue = points.find { it.param.equals(paramName, ignoreCase = true) }?.value
                return strValue?.toDoubleOrNull() // Blir null om det står "NaN"
            }

            FmiForecast(
                temperature = getValue("Temperature"),
                windSpeed = getValue("WindSpeedMS"),
                humidity = getValue("Humidity"),
                totalCloudCover = getValue("TotalCloudCover"),
                precipitation1h = getValue("Precipitation1h"),
                time = Instant.parse(timeStr)
            )
        }.sortedBy { it.time }
    }

    private suspend fun getRawForecast(latitude: Double, longitude: Double): Result<String> = runCatching {
        val latStr = String.format(Locale.US, "%.4f", latitude)
        val lonStr = String.format(Locale.US, "%.4f", longitude)

        // Samma URL som tidigare (vi låter &format=json ligga kvar utifall FMI uppdaterar sina system)
        val url = "https://opendata.fmi.fi/wfs?service=WFS&version=2.0.0&request=getFeature" +
                "&storedquery_id=fmi::forecast::harmonie::surface::point::simple" +
                "&latlon=$latStr,$lonStr" +
                "&parameters=Temperature,WindSpeedMS,Humidity,TotalCloudCover,Precipitation1h" +
                "&format=json"

        httpClient.get(url) {
            header(HttpHeaders.UserAgent, userAgent)
        }.body() // Hämtar det som String istället för en JSON-klass!
    }
}