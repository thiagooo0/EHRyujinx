package org.kenjinx.android

import android.util.Log

/**
 * Allocates controller slots for virtual, motion and physical inputs on Android.
 */
object AndroidControllerRegistry {
    private const val TAG = "ControllerRegistry"
    private const val MAX_CONTROLLERS = 8
    private val slotUsage = BooleanArray(MAX_CONTROLLERS)
    private val physicalControllerIds = mutableMapOf<Int, Int>()
    private val controllerSlots = mutableMapOf<Int, Int>()
    private var virtualControllerId: Int = -1

    @Synchronized
    fun ensureVirtualController(): Int {
        val existingSlot = controllerSlots[virtualControllerId]
        if(virtualControllerId != -1 && existingSlot != null) {
            return virtualControllerId
        }

        if(virtualControllerId != -1) {
            virtualControllerId = -1
        }
        val slot = acquireVirtualSlot()
        if(slot == -1) {
            return -1
        }
        val id = connectController(slot, context = "virtual")
        if(id != -1) {
            slotUsage[slot] = true
            controllerSlots[id] = slot
            virtualControllerId = id
        } else {
            slotUsage[slot] = false
        }

        return virtualControllerId
    }

    /**
     * Ensures that the provided Android device id is associated with a controller slot inside
     * the Switch emulation. If the device is already tracked its controller id is returned.
     */
    @Synchronized
    fun ensurePhysicalController(deviceId: Int): Int {
        if(deviceId == -1) {
            return ensureVirtualController()
        }

        physicalControllerIds[deviceId]?.let { return it }

        ensureVirtualSlotAvailabilityForPhysical()
        val slot = acquireFreeSlot(startIndex = 0)
        val controllerId = if(slot != -1) connectController(slot, context = "deviceId=$deviceId") else -1
        if(slot == -1 || controllerId == -1) {
            Log.w(TAG, "Unable to allocate controller slot for device. deviceId:$deviceId, slot:$slot controllerId:$controllerId")
            return ensureVirtualController()
        }

        slotUsage[slot] = true
        controllerSlots[controllerId] = slot
        physicalControllerIds[deviceId] = controllerId
        Log.d(TAG, "[ensurePhysicalController] connected: ${deviceId} -> ${controllerId}")
        return controllerId
    }

    @Synchronized
    fun getPhysicalControllerId(deviceId: Int): Int? {
        return physicalControllerIds[deviceId]
    }

    /**
     * Releases the controller slot backing the provided Android device id.
     */
    @Synchronized
    fun releasePhysicalController(deviceId: Int) {
        val controllerId = physicalControllerIds.remove(deviceId) ?: return
        Log.d(TAG, "[releasePhysicalController] releasing: ${deviceId} -> ${controllerId}")
        releaseSlot(controllerId, context = "deviceId=$deviceId")
    }

    @Synchronized
    fun releaseAll() {
        val controllersToRelease = controllerSlots.toList()
        for((controllerId, slot) in controllersToRelease) {
            disconnectController(slot, controllerId, context = "releaseAll")
        }
        virtualControllerId = -1
        physicalControllerIds.clear()
        controllerSlots.clear()
        for(i in slotUsage.indices) {
            slotUsage[i] = false
        }
    }

    @Synchronized
    fun releaseVirtualController() {
        if(virtualControllerId != -1) {
            releaseSlot(virtualControllerId, context = "virtual")
        }
    }

    @Synchronized
    fun getConnectedControllerCount(): Int {
        return try {
            KenjinxNative.inputGetConnectedGamepadCount()
        } catch(ex: Throwable) {
            Log.e(TAG, "Failed to query connected controller count", ex)
            0
        }
    }

    private fun acquireFreeSlot(startIndex: Int): Int {
        for(slot in startIndex until MAX_CONTROLLERS) {
            if(!slotUsage[slot]) {
                return slot
            }
        }
        return -1
    }

    private fun connectController(slot: Int, context: String? = null): Int {
        return try {
            val contextSuffix = context?.let { " [$it]" } ?: ""
            Log.d(TAG, "Connecting controller on slot ${slot}$contextSuffix")
            val controllerId = KenjinxNative.inputConnectGamepad(slot)
            if(controllerId != -1) {
                logControllerPoolState("connected", slot, controllerId, context)
            }
            Log.d(TAG, "Connecting controller. controllerId: ${controllerId}")
            controllerId
        } catch(ex: Throwable) {
            Log.e(TAG, "Failed to connect gamepad on slot $slot", ex)
            -1
        }
    }

    private fun releaseSlot(controllerId: Int, context: String? = null) {
        val slot = controllerSlots.remove(controllerId) ?: return
        if(slot in slotUsage.indices) {
            disconnectController(slot, controllerId, context)
            slotUsage[slot] = false
        }
        if(controllerId == virtualControllerId) {
            virtualControllerId = -1
        }
    }

    private fun disconnectController(slot: Int, controllerId: Int, context: String? = null) {
        try {
            val contextSuffix = context?.let { " [$it]" } ?: ""
            Log.d(TAG, "Disconnecting controller on slot ${slot}$contextSuffix")
            KenjinxNative.inputDisconnectGamepad(slot)
            logControllerPoolState("disconnected", slot, controllerId, context)
        } catch(ex: Throwable) {
            Log.e(TAG, "Failed to disconnect gamepad on slot $slot", ex)
        }
    }

    /**
     * Emits a log entry with the up-to-date controller count so it is easy to track connection
     * churn from logcat.
     */
    private fun logControllerPoolState(action: String, slot: Int, controllerId: Int, context: String? = null) {
        val totalControllers = getConnectedControllerCount()
        val contextSuffix = context?.let { " [$it]" } ?: ""
        Log.i(TAG, "Controller $action$contextSuffix (slot=$slot, controllerId=$controllerId) -> total controllers: $totalControllers")
    }

    private fun acquireVirtualSlot(): Int {
        val startIndex = if(physicalControllerIds.isEmpty()) 0 else 1
        var slot = acquireFreeSlot(startIndex)

        if(slot == -1 && startIndex != 0) {
            slot = acquireFreeSlot(0)
        }

        return slot
    }

    private fun ensureVirtualSlotAvailabilityForPhysical() {
        val currentVirtualId = virtualControllerId
        if(currentVirtualId == -1) {
            return
        }
        val virtualSlot = controllerSlots[currentVirtualId]
        if(virtualSlot == null) {
            virtualControllerId = -1
            return
        }

        if(virtualSlot != 0) {
            return
        }
        val newSlot = acquireFreeSlot(startIndex = 1)
        if(newSlot == -1) {
            releaseSlot(currentVirtualId, context = "virtual")
            return
        }
        val newId = connectController(newSlot, context = "virtual-move")
        if(newId == -1) {
            slotUsage[newSlot] = false
            releaseSlot(currentVirtualId, context = "virtual")
            return
        }

        slotUsage[newSlot] = true
        controllerSlots[newId] = newSlot

        releaseSlot(currentVirtualId, context = "virtual")

        virtualControllerId = newId
    }
}
