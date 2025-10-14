package org.kenjinx.android

import android.util.Log

/**
 * Allocates controller slots for virtual, motion and physical inputs on Android.
 */
object AndroidControllerRegistry {
    private const val TAG = "ControllerRegistry"
    private const val MAX_CONTROLLERS = 4
    private const val VIRTUAL_SLOT_INDEX = 0

    private val slotUsage = BooleanArray(MAX_CONTROLLERS)
    private val physicalControllerIds = mutableMapOf<Int, Int>()
    private val controllerSlots = mutableMapOf<Int, Int>()
    private var virtualControllerId: Int = -1

    @Synchronized
    fun ensureVirtualController(): Int {
        if (virtualControllerId != -1) {
            return virtualControllerId
        }

        val id = connectController(VIRTUAL_SLOT_INDEX)
        if (id != -1) {
            slotUsage[VIRTUAL_SLOT_INDEX] = true
            controllerSlots[id] = VIRTUAL_SLOT_INDEX
            virtualControllerId = id
        }

        return virtualControllerId
    }

    @Synchronized
    fun ensurePhysicalController(deviceId: Int): Int {
        if (deviceId == -1) {
            return ensureVirtualController()
        }

        physicalControllerIds[deviceId]?.let { return it }

        val slot = acquireFreeSlot(startIndex = 1)
        val controllerId = if (slot != -1) connectController(slot) else -1
        if (slot == -1 || controllerId == -1) {
            Log.w(TAG, "Unable to allocate controller slot for device $deviceId")
            return ensureVirtualController()
        }

        slotUsage[slot] = true
        controllerSlots[controllerId] = slot
        physicalControllerIds[deviceId] = controllerId

        return controllerId
    }

    @Synchronized
    fun getPhysicalControllerId(deviceId: Int): Int? {
        return physicalControllerIds[deviceId]
    }

    @Synchronized
    fun releasePhysicalController(deviceId: Int) {
        val controllerId = physicalControllerIds.remove(deviceId) ?: return
        releaseSlot(controllerId)
    }

    @Synchronized
    fun releaseAll() {
        virtualControllerId = -1
        physicalControllerIds.clear()
        controllerSlots.clear()
        for (i in slotUsage.indices) {
            slotUsage[i] = false
        }
    }

    @Synchronized
    fun releaseVirtualController() {
        if (virtualControllerId != -1) {
            releaseSlot(virtualControllerId)
        }
    }

    private fun acquireFreeSlot(startIndex: Int): Int {
        for (slot in startIndex until MAX_CONTROLLERS) {
            if (!slotUsage[slot]) {
                return slot
            }
        }
        return -1
    }

    private fun connectController(slot: Int): Int {
        return try {
            KenjinxNative.inputConnectGamepad(slot)
        } catch (ex: Throwable) {
            Log.e(TAG, "Failed to connect gamepad on slot $slot", ex)
            -1
        }
    }

    private fun releaseSlot(controllerId: Int) {
        val slot = controllerSlots.remove(controllerId) ?: return
        if (slot in slotUsage.indices) {
            slotUsage[slot] = false
        }
        if (controllerId == virtualControllerId) {
            virtualControllerId = -1
        }
    }
}
