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
        val id = connectController(slot)
        if(id != -1) {
            slotUsage[slot] = true
            controllerSlots[id] = slot
            virtualControllerId = id
        } else {
            slotUsage[slot] = false
        }

        return virtualControllerId
    }

    @Synchronized
    fun ensurePhysicalController(deviceId: Int): Int {
        if(deviceId == -1) {
            return ensureVirtualController()
        }

        physicalControllerIds[deviceId]?.let { return it }

        ensureVirtualSlotAvailabilityForPhysical()
        val slot = acquireFreeSlot(startIndex = 0)
        val controllerId = if(slot != -1) connectController(slot) else -1
        if(slot == -1 || controllerId == -1) {
            Log.w(TAG, "Unable to allocate controller slot for device $deviceId")
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
        for(i in slotUsage.indices) {
            slotUsage[i] = false
        }
    }

    @Synchronized
    fun releaseVirtualController() {
        if(virtualControllerId != -1) {
            releaseSlot(virtualControllerId)
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

    private fun connectController(slot: Int): Int {
        return try {
            Log.d(TAG, "GamepadManager connectController: ${slot}")
            KenjinxNative.inputConnectGamepad(slot)
        } catch(ex: Throwable) {
            Log.e(TAG, "Failed to connect gamepad on slot $slot", ex)
            -1
        }
    }

    private fun releaseSlot(controllerId: Int) {
        val slot = controllerSlots.remove(controllerId) ?: return
        if(slot in slotUsage.indices) {
            slotUsage[slot] = false
        }
        if(controllerId == virtualControllerId) {
            virtualControllerId = -1
        }
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
            releaseSlot(currentVirtualId)
            return
        }
        val newId = connectController(newSlot)
        if(newId == -1) {
            slotUsage[newSlot] = false
            releaseSlot(currentVirtualId)
            return
        }

        slotUsage[newSlot] = true
        controllerSlots[newId] = newSlot

        releaseSlot(currentVirtualId)

        virtualControllerId = newId
    }
}
