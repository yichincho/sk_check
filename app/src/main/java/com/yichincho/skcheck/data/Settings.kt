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
    /** A rule that stays hit notifies again after this many hours… */
    val notifyGapHours: Int = 1,
    /** …but no more than this many times a day. */
    val notifyMaxPerDay: Int = 3,
    val geminiKey: String = "",
    val geminiModel: String = Gemini.DEFAULT_MODEL,
    val geminiModels: List<String> = listOf(Gemini.DEFAULT_MODEL),
    val deepseekKey: String = "",
    val deepseekModel: String = DeepSeek.DEFAULT_MODEL,
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
            notifyGapHours = prefs.getInt("notifyGapHours", d.notifyGapHours),
            notifyMaxPerDay = prefs.getInt("notifyMaxPerDay", d.notifyMaxPerDay),
            geminiKey = prefs.getString("geminiKey", d.geminiKey)!!,
            geminiModel = prefs.getString("geminiModel", d.geminiModel)!!,
            geminiModels = prefs.getString("geminiModels", null)?.split('\n')?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() } ?: d.geminiModels,
            deepseekKey = prefs.getString("deepseekKey", d.deepseekKey)!!,
            deepseekModel = prefs.getString("deepseekModel", d.deepseekModel)!!,
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
            .putInt("notifyGapHours", s.notifyGapHours)
            .putInt("notifyMaxPerDay", s.notifyMaxPerDay)
            .putString("geminiKey", s.geminiKey)
            .putString("geminiModel", s.geminiModel)
            .putString("geminiModels", s.geminiModels.joinToString("\n"))
            .putString("deepseekKey", s.deepseekKey)
            .putString("deepseekModel", s.deepseekModel)
            .apply()
    }
}
