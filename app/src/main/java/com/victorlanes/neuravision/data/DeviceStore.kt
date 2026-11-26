package com.victorlanes.neuravision.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.victorlanes.neuravision.model.Device

/** Guarda os equipamentos localmente no celular (JSON em SharedPreferences). Simples e sem servidor. */
class DeviceStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("neuravision", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun all(): MutableList<Device> {
        val json = prefs.getString(KEY, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Device>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun get(id: String): Device? = all().firstOrNull { it.id == id }

    fun save(device: Device) {
        val list = all()
        val i = list.indexOfFirst { it.id == device.id }
        if (i >= 0) list[i] = device else list.add(device)
        persist(list)
    }

    fun delete(id: String) {
        val list = all().apply { removeAll { it.id == id } }
        persist(list)
    }

    private fun persist(list: List<Device>) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }

    companion object {
        private const val KEY = "devices"
    }
}
