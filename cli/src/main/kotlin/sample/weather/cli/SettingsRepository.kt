package sample.weather.cli

interface SettingsRepository {
    fun loadSettings(): Settings

    fun saveSettings(settings: Settings): Boolean
}