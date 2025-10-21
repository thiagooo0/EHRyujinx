package org.kenjinx.android.controllers

import android.view.InputDevice
import java.util.UUID

/**
 * Persisted profile describing how a specific physical controller (identified via its
 * descriptor) should be used by the emulator.
 */
data class ControllerProfile(
    val id: String = UUID.randomUUID().toString(),
    val descriptor: String,
    val deviceName: String,
    val type: ControllerDeviceType,
    val mode: ControllerMode = ControllerMode.Unmatched,
    val pairedDescriptor: String? = null,
) {
    val isJoyCon: Boolean
        get() = type == ControllerDeviceType.JoyConLeft || type == ControllerDeviceType.JoyConRight

    val isMatched: Boolean
        get() = when(mode) {
            ControllerMode.Unmatched -> false
            ControllerMode.GenericMatched,
            ControllerMode.JoyConSingle,
            ControllerMode.JoyConPairedPrimary,
            ControllerMode.JoyConPairedSecondary -> true
        }

    fun isPrimary(): Boolean = mode == ControllerMode.GenericMatched ||
        mode == ControllerMode.JoyConSingle ||
        mode == ControllerMode.JoyConPairedPrimary
}

/**
 * UI facing status information for a controller profile. Indicates the current connection
 * state for visualisation on the matching screen.
 */
data class ControllerStatus(
    val profile: ControllerProfile,
    val isConnected: Boolean,
)

/**
 * Utility helper that classifies an Android [InputDevice] into one of the supported device
 * types.
 */
fun classifyDevice(device: InputDevice): ControllerDeviceType {
    return classifyDeviceName(device.name ?: "")
}

fun classifyDeviceName(name: String): ControllerDeviceType {
    val normalized = name.lowercase()
    val hasJoyConTag = normalized.contains("joy-con") || normalized.contains("joy con")
    val hasLeftTag = normalized.contains("(l)") || normalized.contains(" left")
    val hasRightTag = normalized.contains("(r)") || normalized.contains(" right")

    return when {
        hasJoyConTag && hasLeftTag -> ControllerDeviceType.JoyConLeft
        hasJoyConTag && hasRightTag -> ControllerDeviceType.JoyConRight
        else -> ControllerDeviceType.Generic
    }
}
