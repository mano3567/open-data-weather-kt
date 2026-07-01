package sample.weather.cli

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NamedLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class Settings(
    val apiKeys: Map<String, String> = emptyMap(),
    val currentLocationId: String? = null,
    val savedLocations: List<NamedLocation> = emptyList(),
    val userAgent: String?
) {

    val currentLocation: NamedLocation?
        get() = savedLocations.find { it.id == currentLocationId }

    fun getApiKey(providerName: String): String? {
        return apiKeys[providerName.uppercase()]
    }
}