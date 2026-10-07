package `in`.mite.rain.platform

import android.content.Context
import `in`.mite.rain.core.LogisticRiskModel
import `in`.mite.rain.core.RiskFeatures
import `in`.mite.rain.core.RiskModel
import org.json.JSONArray
import org.json.JSONObject

object RiskModelLoader {
    fun load(context: Context): RiskModel {
        val json = JSONObject(context.assets.open("risk_model.json").bufferedReader().use { it.readText() })
        require(json.getString("type") == "logistic") { "Unsupported risk model type" }
        val features = json.getJSONArray("features")
        require((0 until features.length()).map { features.getString(it) } == RiskFeatures.names) { "Risk feature order mismatch" }
        val scaler = json.getJSONObject("scaler")
        return LogisticRiskModel(numbers(json.getJSONArray("weights")), json.getDouble("bias"),
            numbers(scaler.getJSONArray("mean")), numbers(scaler.getJSONArray("std")))
    }
    private fun numbers(array: JSONArray): List<Double> = (0 until array.length()).map { array.getDouble(it) }
}
