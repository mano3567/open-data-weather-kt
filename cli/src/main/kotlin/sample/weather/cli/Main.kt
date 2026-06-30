package sample.weather.cli

import se.metricspace.opendata.geolocation.GeoLocationService
import kotlinx.coroutines.runBlocking
import se.metricspace.opendata.weather.SmhiService
import javax.xml.stream.Location

fun main() {
    val geoLocationService = GeoLocationService(userAgent = "metricspace.location/1.0.2")
    val smhiService = SmhiService("metricspace.location/1.0.2")

    println("--- Välkommen till GeoService CLI ---")
    println("Skriv namnet på en plats för att slå upp den, eller 'q' för att avsluta.")

    runBlocking {
        while (true) {
            print("\nVal (a för att avsluta): ")

            // readlnOrNull returnerar null om input-strömmen stängs (t.ex. Ctrl+D)
            val input = readlnOrNull()?.trim() ?: break

            if (input.equals("a", ignoreCase = true)) break
            if (input.isBlank()) continue

            println("Söker efter '$input'...")
            val location = geoLocationService.findLocation(input)

            if (location != null) {
                println("Resultat: ${location.displayName}")
                println("Lat/Lon: ${location.latitude}, ${location.longitude}")
            } else {
                println("Hittade tyvärr inget för '$input'.")
            }
        }
    }
}
