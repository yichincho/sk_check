package com.yichincho.skcheck.data

import android.content.Context

data class Settings(
    val nvidiaKey: String = "",
    /** Up to five vision models, best first, as found the last time the key was tested. */
    val visionModels: List<String> = Nvidia.DEFAULT_MODELS,
    val visionModel: String = Nvidia.DEFAULT_MODELS.first(),
    /** Minutes between checks during market hours: 5, 10 or 15. */
    val intervalMin: Int = 15,
    val defaultDrop: Double = 5.0,
    val defaultRise: Double = 8.0,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        val models = prefs.getString("visionModels", null)?.split('\n')?.filter { it.isNotBlank() }
        return Settings(
            nvidiaKey = prefs.getString("nvidiaKey", d.nvidiaKey)!!,
            visionModels = if (models.isNullOrEmpty()) d.visionModels else models,
            visionModel = prefs.getString("visionModel", d.visionModel)!!,
            intervalMin = prefs.getInt("intervalMin", d.intervalMin),
            defaultDrop = prefs.getFloat("defaultDrop", d.defaultDrop.toFloat()).toDouble(),
            defaultRise = prefs.getFloat("defaultRise", d.defaultRise.toFloat()).toDouble(),
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putString("nvidiaKey", s.nvidiaKey)
            .putString("visionModels", s.visionModels.joinToString("\n"))
            .putString("visionModel", s.visionModel)
            .putInt("intervalMin", s.intervalMin)
            .putFloat("defaultDrop", s.defaultDrop.toFloat())
            .putFloat("defaultRise", s.defaultRise.toFloat())
            .apply()
    }
}
