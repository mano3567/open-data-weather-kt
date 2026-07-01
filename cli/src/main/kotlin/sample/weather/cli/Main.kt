package sample.weather.cli

import se.metricspace.opendata.geolocation.GeoLocationService
import kotlinx.coroutines.runBlocking
import se.metricspace.opendata.weather.SmhiService
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun main() {
    println("⛅ Välkommen till WeatherCLI ⛅")
    val settingsRepo: SettingsRepository = JsonFileSettingsRepository("weather_config.json")
    var currentSettings = settingsRepo.loadSettings()
    while (currentSettings.userAgent.isNullOrBlank()) {
        print("Du behöver välja en User-Agent: ")
        val someUserAgent = readlnOrNull()?.trim()
        if (!someUserAgent.isNullOrBlank()) {
            val newSettings = currentSettings.copy(userAgent = someUserAgent.trim())
            settingsRepo.saveSettings(newSettings)
            currentSettings = settingsRepo.loadSettings()
        } else {
            println("User-Agent kan inte vara tom. Försök igen.")
        }
    }
    val geoLocationService = GeoLocationService(userAgent = currentSettings.userAgent)
    val smhiService = SmhiService(currentSettings.userAgent)

    runBlocking {
        while(null == currentSettings.currentLocation) {
            println("Ingen plats är vald än...")
            print("Ange en plats, eller q för att avsluta:")
            val input = readlnOrNull()?.trim() ?: break
            if (input.equals("q", ignoreCase = true)) break
            if (input.isBlank()) continue
            val location = geoLocationService.findLocation(input)
            if(null!=location) {
                val namedLocation = NamedLocation(name = location.displayName, latitude = location.latitude, longitude = location.longitude)
                var namedLocations = currentSettings.savedLocations.plus(namedLocation)
                val newSettings = currentSettings.copy(savedLocations = namedLocations, currentLocationId = namedLocation.id)
                settingsRepo.saveSettings(newSettings)
                currentSettings = settingsRepo.loadSettings()
            }
        }
        while(true) {
            val currentLoc = currentSettings.currentLocation

            println("\n==================================")
            println(" Aktuell plats: ${currentLoc?.name ?: "Ingen vald"}")
            println("==================================")
            println("1. Hämta väder för aktuell plats")
            println("2. Välj befintlig plats")
            println("3. Lägg till ny plats")
            println("Q. Avsluta")
            print("Välj ett alternativ: ")
            when (readlnOrNull()?.trim()) {
                "1" -> {
                    currentLoc?.let { location ->
                        println("\n⏳ Hämtar väder för ${currentLoc.name} (${currentLoc.latitude}, ${currentLoc.longitude})...")
                        val forecasts = smhiService.getSmhiForecast(currentLoc.latitude , currentLoc.longitude)
                        forecasts.onSuccess { forecastList ->
                            println("Väderprognos:")
                            forecastList.take(6).forEach { forecast ->
                                val zoneId = ZoneId.systemDefault()
                                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                val zonedDateTime = forecast.time.atZone(zoneId)
                                println("${zonedDateTime.format(formatter)} | Sikt: ${forecast.visibilityKm} km | Vind: ${forecast.windSpeed} m/s | Precip: ${forecast.precipitationMm} mm |  Humidity: ${forecast.humidity} | Octas: ${forecast.cloudCoverOctas}/8 | Temperature: ${forecast.temperature}")
                            }
                        }.onFailure { error ->
                            println("Kunde inte hämta väderprognos: ${error.message}")
                        }
                    } ?: println("❌ Ingen plats vald!")
                }
                "2" -> {
                    if (currentSettings.savedLocations.isEmpty()) {
                        println("⚠️ Du har inga sparade platser. Välj alternativ 3 för att lägga till en.")
                    } else {
                        println("\nSparade platser:")
                        currentSettings.savedLocations.forEachIndexed { index, loc ->
                            println("  ${index + 1}. ${loc.name}")
                        }
                        print("Välj nummer: ")

                        val choice = readlnOrNull()?.toIntOrNull()
                        if (choice != null && choice in 1..currentSettings.savedLocations.size) {
                            val selectedLoc = currentSettings.savedLocations[choice - 1]
                            currentSettings = currentSettings.copy(currentLocationId = selectedLoc.id)
                            settingsRepo.saveSettings(currentSettings)
                            println("✅ Ändrade aktiv plats till ${selectedLoc.name}")
                        } else {
                            println("❌ Ogiltigt val.")
                        }
                    }
                }
                "3" -> {
                    println("\n--- Lägg till ny plats ---")
                    print("Ange plats (till exempel Sergels torg i stockholm) ")
                    val somePlace = readlnOrNull()?.trim() ?: ""
                    val newLocation = geoLocationService.findLocation(somePlace)
                    if(null!=newLocation) {
                        println(newLocation)
                        val newLoc = NamedLocation(name = newLocation.name, latitude = newLocation.latitude, longitude = newLocation.longitude)
                        val updatedLocations = currentSettings.savedLocations + newLoc
                        currentSettings = currentSettings.copy(
                            savedLocations = updatedLocations,
                            currentLocationId = currentSettings.currentLocationId ?: newLoc.id
                        )
                        settingsRepo.saveSettings(currentSettings)
                        println("✅ Lade till och sparade ${newLocation.name}")
                    } else {
                        println("Kunde inte hitta '${somePlace}'. Se till att skriva in en korrekt plats.")
                    }
                }
                "Q", "q", "A", "a" -> {
                    println("Avslutar... Hejdå! 👋")
                    break
                }
                else -> {
                    println("❌ Förstod inte det där. Försök igen ...")
                }
            }
        }
    }
    smhiService.close()
    geoLocationService.close()
}
