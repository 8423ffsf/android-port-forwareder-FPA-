package yuki.androidportforwarder.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import yuki.androidportforwarder.data.model.ForwardRule

class RuleRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ForwardRules", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveRules(rules: List<ForwardRule>) {
        prefs.edit().putString("rules", gson.toJson(rules)).apply()
    }

    fun loadRules(): List<ForwardRule> {
        return gson.fromJson<List<ForwardRule>>(
            prefs.getString("rules", "[]"),
            object : TypeToken<List<ForwardRule>>() {}.type
        ) ?: emptyList()
    }
}