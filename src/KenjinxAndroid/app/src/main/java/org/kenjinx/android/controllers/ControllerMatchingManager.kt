package org.kenjinx.android.controllers

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kenjinx.android.GamepadManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates the Android side controller matching logic. The manager keeps track of which
 * devices are connected, how they should be exposed to the emulator and bridges pairing events
 * originating from the UI.
 */
object ControllerMatchingManager {
    private val joyConSlKeyCode: Int? = KeyEvent.keyCodeFromString("KEYCODE_BUTTON_SL").takeIf { it != KeyEvent.KEYCODE_UNKNOWN }
    private val joyConSrKeyCode: Int? = KeyEvent.keyCodeFromString("KEYCODE_BUTTON_SR").takeIf { it != KeyEvent.KEYCODE_UNKNOWN }

    private fun isJoyConSlKey(keyCode: Int): Boolean {
        if(keyCode == KeyEvent.KEYCODE_BUTTON_1) {
            return true
        }
        return joyConSlKeyCode?.let { keyCode == it } ?: false
    }

    private fun isJoyConSrKey(keyCode: Int): Boolean {
        if(keyCode == KeyEvent.KEYCODE_BUTTON_2) {
            return true
        }
        return joyConSrKeyCode?.let { keyCode == it } ?: false
    }
    private lateinit var store: ControllerMatchingStore
    private val profiles = MutableStateFlow<List<ControllerProfile>>(emptyList())
    private val statuses = MutableStateFlow<List<ControllerStatus>>(emptyList())
    private val pairingState = MutableStateFlow(false)

    private val descriptorByDeviceId = ConcurrentHashMap<Int, String>()
    private val deviceIdByDescriptor = ConcurrentHashMap<String, Int>()
    private val controllerIdByDevice = ConcurrentHashMap<Int, Int>()

    private var gamepadManager: GamepadManager? = null

    private val pairingActive = AtomicBoolean(false)
    private var lastLeftPress: Pair<String, Long>? = null
    private var lastRightPress: Pair<String, Long>? = null

    fun initialize(context: Context) {
        if(::store.isInitialized) {
            return
        }
        store = ControllerMatchingStore(context)
        profiles.value = store.load()
        store.save(profiles.value)
        refreshStatuses()
    }

    fun profilesFlow(): StateFlow<List<ControllerProfile>> = profiles.asStateFlow()
    fun statusesFlow(): StateFlow<List<ControllerStatus>> = statuses.asStateFlow()
    fun pairingStateFlow(): StateFlow<Boolean> = pairingState.asStateFlow()

    fun setGamepadManager(manager: GamepadManager?) {
        gamepadManager = manager
    }

    fun clearRuntimeState() {
        controllerIdByDevice.clear()
        descriptorByDeviceId.clear()
        deviceIdByDescriptor.clear()
        pairingActive.set(false)
        pairingState.value = false
        lastLeftPress = null
        lastRightPress = null
        refreshStatuses()
    }

    fun onDeviceSeen(device: InputDevice) {
        val descriptor = device.descriptor ?: return
        descriptorByDeviceId[device.id] = descriptor
        deviceIdByDescriptor[descriptor] = device.id
        ensureProfileExists(device)
        refreshStatuses(descriptor, isConnected = true)
    }

    fun shouldRegisterDevice(device: InputDevice): Boolean {
        val descriptor = device.descriptor ?: return false
        val profile = findProfile(descriptor) ?: return false
        return when(profile.mode) {
            ControllerMode.Unmatched -> false
            ControllerMode.GenericMatched -> true
            ControllerMode.JoyConSingle -> true
            ControllerMode.JoyConPairedPrimary -> true
            ControllerMode.JoyConPairedSecondary -> false
        }
    }

    fun onControllerAllocated(device: InputDevice, controllerId: Int) {
        val descriptor = device.descriptor ?: return
        controllerIdByDevice[device.id] = controllerId
        val profile = findProfile(descriptor) ?: return
        if(profile.mode == ControllerMode.JoyConPairedPrimary && profile.pairedDescriptor != null) {
            val secondaryId = deviceIdByDescriptor[profile.pairedDescriptor]
            if(secondaryId != null) {
                controllerIdByDevice[secondaryId] = controllerId
            }
        }
        refreshStatuses()
    }

    fun onControllerReleased(deviceId: Int) {
        val descriptor = descriptorByDeviceId[deviceId]
        controllerIdByDevice.remove(deviceId)
        descriptor?.let {
            refreshStatuses(it, isConnected = false)
            val profile = findProfile(it)
            if(profile?.mode == ControllerMode.JoyConPairedPrimary && profile.pairedDescriptor != null) {
                val secondaryId = deviceIdByDescriptor[profile.pairedDescriptor]
                if(secondaryId != null) {
                    controllerIdByDevice.remove(secondaryId)
                }
            }
        }
    }

    fun onDeviceDisconnected(deviceId: Int) {
        val descriptor = descriptorByDeviceId.remove(deviceId) ?: return
        deviceIdByDescriptor.remove(descriptor)
        controllerIdByDevice.remove(deviceId)
        refreshStatuses(descriptor, isConnected = false)
        val profile = findProfile(descriptor)
        if(profile?.mode == ControllerMode.JoyConPairedPrimary && profile.pairedDescriptor != null) {
            // Clear the secondary controller id when the primary leaves.
            val secondaryId = deviceIdByDescriptor[profile.pairedDescriptor]
            if(secondaryId != null) {
                controllerIdByDevice.remove(secondaryId)
            }
        }
    }

    fun getRuntimeInfo(deviceId: Int): ControllerRuntimeInfo? {
        val descriptor = descriptorByDeviceId[deviceId] ?: return null
        val profile = findProfile(descriptor) ?: return null
        val controllerId = controllerIdByDevice[deviceId] ?: return null
        return ControllerRuntimeInfo(profile, controllerId)
    }

    fun getProfileForDescriptor(descriptor: String): ControllerProfile? = findProfile(descriptor)

    fun toggleGenericMatch(descriptor: String) {
        updateProfiles { list ->
            list.map {
                if(it.descriptor == descriptor && it.type == ControllerDeviceType.Generic) {
                    val newMode = if(it.mode == ControllerMode.GenericMatched) ControllerMode.Unmatched else ControllerMode.GenericMatched
                    it.copy(mode = newMode)
                } else {
                    it
                }
            }
        }
    }

    fun setJoyConSingle(descriptor: String) {
        val profile = findProfile(descriptor) ?: return
        val updates = mutableListOf<(ControllerProfile) -> ControllerProfile>()
        if(profile.type == ControllerDeviceType.JoyConLeft || profile.type == ControllerDeviceType.JoyConRight) {
            updates += { p: ControllerProfile ->
                if(p.descriptor == descriptor) p.copy(mode = ControllerMode.JoyConSingle, pairedDescriptor = null) else p
            }
            profile.pairedDescriptor?.let { otherDescriptor ->
                updates += { p: ControllerProfile ->
                    if(p.descriptor == otherDescriptor) p.copy(mode = ControllerMode.JoyConSingle, pairedDescriptor = null) else p
                }
            }
            applyUpdates(updates)
        }
    }

    fun clearJoyConPair(descriptor: String) {
        val profile = findProfile(descriptor) ?: return
        if(!profile.isJoyCon) return
        val updates = mutableListOf<(ControllerProfile) -> ControllerProfile>()
        updates += { p: ControllerProfile ->
            if(p.descriptor == descriptor) p.copy(mode = ControllerMode.JoyConSingle, pairedDescriptor = null) else p
        }
        profile.pairedDescriptor?.let { otherDescriptor ->
            updates += { p: ControllerProfile ->
                if(p.descriptor == otherDescriptor) p.copy(mode = ControllerMode.JoyConSingle, pairedDescriptor = null) else p
            }
        }
        applyUpdates(updates)
    }

    fun startJoyConPairing(): Boolean {
        if(pairingActive.get()) {
            return false
        }
        pairingActive.set(true)
        pairingState.value = true
        lastLeftPress = null
        lastRightPress = null
        return true
    }

    fun cancelJoyConPairing() {
        pairingActive.set(false)
        pairingState.value = false
        lastLeftPress = null
        lastRightPress = null
    }

    fun isPairingActive(): Boolean = pairingActive.get()

    fun handlePairingKey(event: KeyEvent): Boolean {
        if(!pairingActive.get()) {
            return false
        }
        if(event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        val device = event.device ?: return false
        val descriptor = device.descriptor ?: return false
        val type = classifyDevice(device)
        val now = SystemClock.uptimeMillis()
        when(type) {
            ControllerDeviceType.JoyConLeft -> {
                if(
                    event.keyCode == KeyEvent.KEYCODE_BUTTON_L1 ||
                    isJoyConSlKey(event.keyCode)
                ) {
                    lastLeftPress = descriptor to now
                    attemptCompletePairing()
                    return true
                }
            }
            ControllerDeviceType.JoyConRight -> {
                if(
                    event.keyCode == KeyEvent.KEYCODE_BUTTON_R1 ||
                    isJoyConSrKey(event.keyCode)
                ) {
                    lastRightPress = descriptor to now
                    attemptCompletePairing()
                    return true
                }
            }
            ControllerDeviceType.Generic -> {}
        }
        return false
    }

    fun setJoyConPair(leftDescriptor: String, rightDescriptor: String) {
        applyUpdates(
            listOf(
                { p: ControllerProfile ->
                    if(p.descriptor == leftDescriptor) p.copy(mode = ControllerMode.JoyConPairedPrimary, pairedDescriptor = rightDescriptor) else p
                },
                { p: ControllerProfile ->
                    if(p.descriptor == rightDescriptor) p.copy(mode = ControllerMode.JoyConPairedSecondary, pairedDescriptor = leftDescriptor) else p
                }
            )
        )
    }

    private fun attemptCompletePairing() {
        val left = lastLeftPress
        val right = lastRightPress
        if(left == null || right == null) {
            return
        }
        val window = 1500L
        if(kotlin.math.abs(left.second - right.second) > window) {
            return
        }
        ensureProfileExists(left.first, ControllerDeviceType.JoyConLeft)
        ensureProfileExists(right.first, ControllerDeviceType.JoyConRight)
        setJoyConPair(left.first, right.first)
        pairingActive.set(false)
        pairingState.value = false
        gamepadManager?.reregisterConnectedGamepads()
    }

    private fun ensureProfileExists(device: InputDevice) {
        val descriptor = device.descriptor ?: return
        ensureProfileExists(descriptor, classifyDevice(device), device.name ?: "")
    }

    private fun ensureProfileExists(descriptor: String, type: ControllerDeviceType, name: String = "") {
        val existing = findProfile(descriptor)
        if(existing != null) {
            val updates = mutableListOf<(ControllerProfile) -> ControllerProfile>()
            if(name.isNotEmpty() && existing.deviceName != name) {
                updates += { profile: ControllerProfile ->
                    if(profile.descriptor == descriptor) profile.copy(deviceName = name) else profile
                }
            }
            if(existing.type != type) {
                updates += { profile: ControllerProfile ->
                    if(profile.descriptor != descriptor) {
                        profile
                    } else {
                        val sanitizedMode = when(type) {
                            ControllerDeviceType.Generic -> when(profile.mode) {
                                ControllerMode.GenericMatched -> ControllerMode.GenericMatched
                                else -> ControllerMode.Unmatched
                            }
                            ControllerDeviceType.JoyConLeft, ControllerDeviceType.JoyConRight -> when(profile.mode) {
                                ControllerMode.JoyConSingle,
                                ControllerMode.JoyConPairedPrimary,
                                ControllerMode.JoyConPairedSecondary -> profile.mode
                                ControllerMode.GenericMatched,
                                ControllerMode.Unmatched -> ControllerMode.JoyConSingle
                            }
                        }
                        val sanitizedPair = when(type) {
                            ControllerDeviceType.Generic -> null
                            ControllerDeviceType.JoyConLeft, ControllerDeviceType.JoyConRight -> when(sanitizedMode) {
                                ControllerMode.JoyConPairedPrimary,
                                ControllerMode.JoyConPairedSecondary -> profile.pairedDescriptor
                                else -> null
                            }
                        }
                        profile.copy(type = type, mode = sanitizedMode, pairedDescriptor = sanitizedPair)
                    }
                }
            }
            if(updates.isNotEmpty()) {
                applyUpdates(updates)
            }
            return
        }
        val initialMode = when(type) {
            ControllerDeviceType.Generic -> ControllerMode.Unmatched
            ControllerDeviceType.JoyConLeft, ControllerDeviceType.JoyConRight -> ControllerMode.JoyConSingle
        }
        updateProfiles { current ->
            current + ControllerProfile(
                descriptor = descriptor,
                deviceName = if(name.isNotEmpty()) name else descriptor,
                type = type,
                mode = initialMode
            )
        }
    }

    private fun applyUpdates(updaters: List<(ControllerProfile) -> ControllerProfile>) {
        if(updaters.isEmpty()) return
        updateProfiles { current ->
            current.map { profile ->
                updaters.fold(profile) { acc, update -> update(acc) }
            }
        }
        gamepadManager?.reregisterConnectedGamepads()
    }

    private fun updateProfiles(update: (List<ControllerProfile>) -> List<ControllerProfile>) {
        val updated = update(profiles.value)
        profiles.value = updated
        store.save(updated)
        refreshStatuses()
    }

    private fun refreshStatuses(descriptor: String? = null, isConnected: Boolean? = null) {
        if(descriptor != null && isConnected != null) {
            // Fast path for single profile update
            val newStatuses = statuses.value.toMutableList()
            val index = newStatuses.indexOfFirst { it.profile.descriptor == descriptor }
            if(index >= 0) {
                val existing = newStatuses[index]
                newStatuses[index] = existing.copy(isConnected = isConnected)
                statuses.value = newStatuses
                return
            }
        }
        val descriptors = deviceIdByDescriptor.keys
        statuses.value = profiles.value.map { profile ->
            ControllerStatus(
                profile = profile,
                isConnected = descriptors.contains(profile.descriptor)
            )
        }
    }

    private fun findProfile(descriptor: String): ControllerProfile? {
        return profiles.value.firstOrNull { it.descriptor == descriptor }
    }
}

/**
 * Runtime lookup result returned for every controller event. Contains the persisted profile and
 * the controller id assigned inside the emulator.
 */
data class ControllerRuntimeInfo(
    val profile: ControllerProfile,
    val controllerId: Int,
)
