package org.kenjinx.android.controllers

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val PREF_KEY_PROFILES = "controller_matching_profiles"

/**
 * Persists controller matching data inside the shared preferences. The information is stored as
 * a JSON array so we can evolve the schema without breaking existing installations.
 */
class ControllerMatchingStore(private val context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun load(): List<ControllerProfile> {
        val raw = preferences.getString(PREF_KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for(i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(obj.toProfile())
                }
            }
        }.getOrElse { emptyList() }
    }

    fun save(profiles: List<ControllerProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        preferences.edit().putString(PREF_KEY_PROFILES, array.toString()).apply()
    }

    private fun ControllerProfile.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("descriptor", descriptor)
            put("deviceName", deviceName)
            put("type", type.name)
            put("mode", mode.name)
            put("pairedDescriptor", pairedDescriptor)
        }
    }

    private fun JSONObject.toProfile(): ControllerProfile {
        val storedId = optString("id")
        val storedType = ControllerDeviceType.valueOf(getString("type"))
        val deviceName = optString("deviceName")
        val inferredType = if(deviceName.isNullOrEmpty()) storedType else classifyDeviceName(deviceName)
        val normalizedType = when {
            storedType == ControllerDeviceType.Generic && inferredType != ControllerDeviceType.Generic -> inferredType
            else -> storedType
        }
        val rawMode = ControllerMode.valueOf(getString("mode"))
        val normalizedMode = when(normalizedType) {
            ControllerDeviceType.JoyConLeft, ControllerDeviceType.JoyConRight -> when(rawMode) {
                ControllerMode.GenericMatched,
                ControllerMode.Unmatched -> ControllerMode.JoyConSingle
                else -> rawMode
            }
            ControllerDeviceType.Generic -> when(rawMode) {
                ControllerMode.JoyConSingle,
                ControllerMode.JoyConPairedPrimary,
                ControllerMode.JoyConPairedSecondary -> ControllerMode.Unmatched
                else -> rawMode
            }
        }
        val pairedDescriptor = optString("pairedDescriptor").ifEmpty { null }
        val normalizedPair = if(
            normalizedMode != ControllerMode.JoyConPairedPrimary &&
            normalizedMode != ControllerMode.JoyConPairedSecondary
        ) {
            null
        } else {
            pairedDescriptor
        }
        return ControllerProfile(
            id = if(storedId.isNullOrEmpty()) UUID.randomUUID().toString() else storedId,
            descriptor = getString("descriptor"),
            deviceName = deviceName,
            type = normalizedType,
            mode = normalizedMode,
            pairedDescriptor = normalizedPair
        )
    }
}
