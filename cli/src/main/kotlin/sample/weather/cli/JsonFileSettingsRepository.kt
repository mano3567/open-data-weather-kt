package sample.weather.cli

import kotlinx.serialization.json.Json
import java.io.File

class JsonFileSettingsRepository(
    private val filePath: String = "configweather.json"
) : SettingsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun loadSettings(): Settings {
        val file = File(filePath)
        if (!file.exists()) {
            return Settings(userAgent = null)
        }

        return try {
            json.decodeFromString<Settings>(file.readText())
        } catch (e: Exception) {
            println("Fel vid inläsning av settings: ${e.message}")
            Settings(userAgent = "MetricspaceWeather/1.0")
        }
    }

    override fun saveSettings(settings: Settings): Boolean {
        return try {
            val file = File(filePath)
            val jsonString = json.encodeToString(settings)
            file.writeText(jsonString)
            true
        } catch (e: Exception) {
            println("Kunde inte spara settings: ${e.message}")
            false
        }
    }
}