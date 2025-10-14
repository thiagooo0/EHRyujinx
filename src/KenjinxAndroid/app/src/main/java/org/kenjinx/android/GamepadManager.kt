package org.kenjinx.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice

/**
 * is current input device is gamepad
 */
val InputDevice.isGamepad: Boolean
    get() {
        val sources = this.sources
        return (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

class GamepadManager(context: Context): InputManager.InputDeviceListener {
    /** Dedicated tag for structured logging. */
    private val tag = "GamepadManager"

    /** System level input manager that notifies us of hardware changes. */
    private val inputManager: InputManager =
        context.getSystemService(Context.INPUT_SERVICE) as InputManager

    /**
     * Cached sensor listeners keyed by device id. They are shared between all registered
     * sensors for a single controller so we can cleanly unregister when the pad leaves.
     */
    val sensorEventListeners = HashMap<Int, MySensorEventListener>()

    /** Flag that indicates whether we should forward motion data to the Switch. */
    private var isSendSensor = false

    /**
     * Tracks the mapping from Android input device id to the controller id assigned inside
     * the Switch emulation layer.
     */
    private val connectedGamepads = mutableMapOf<Int, ConnectedGamepad>()

    /**
     * Tracks the metadata we need for a physical controller bound to the emulator.
     */
    private data class ConnectedGamepad(
        val device: InputDevice,
        val controllerId: Int,
        val sensors: MutableList<Sensor> = mutableListOf()
    )

    /**
     * start listening gamepad change
     */
    fun startListening() {
        inputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        checkForConnectedGamepads()
    }

    /**
     * stop listening gamepad change
     */
    fun stopListening() {
        inputManager.unregisterInputDeviceListener(this)
    }

    /**
     * start send sensor to switch
     */
    fun startSendSensor() {
        isSendSensor = true
    }

    /**
     * stop send sensor to switch
     */
    fun stopSendSensor() {
        isSendSensor = false
    }

    /**
     * Walks through the currently reported devices and registers/unregisters gamepads to keep
     * LibKenjinx in sync with Android's view of the world.
     */
    private fun checkForConnectedGamepads() {
        val deviceIds = inputManager.inputDeviceIds
        val seenDevices = mutableSetOf<Int>()
        for(deviceId in deviceIds) {
            InputDevice.getDevice(deviceId)?.let { device ->
                if(device.isGamepad) {
                    seenDevices.add(device.id)
                    if(!connectedGamepads.containsKey(device.id)) {
                        registerGamepad(device)
                    }
                }
            }
        }

        val disconnected = connectedGamepads.keys - seenDevices
        for(deviceId in disconnected) {
            unregisterGamepad(deviceId)
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        Log.d(tag, "Gamepad added: ${deviceId}")
        checkForConnectedGamepads()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        Log.d(tag, "Gamepad removed: $deviceId")
        checkForConnectedGamepads()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        Log.d(tag, "Gamepad changed: $deviceId")
        checkForConnectedGamepads()
    }

    /**
     * Requests a controller slot from the registry, registers any sensors for motion input and
     * records the allocation so we can undo it later.
     */
    private fun registerGamepad(device: InputDevice) {
        val controllerId = AndroidControllerRegistry.ensurePhysicalController(device.id)
        if(controllerId == -1) {
            Log.w(tag, "Failed to allocate controller for device ${device.id}")
            return
        }

        val connectedGamepad = ConnectedGamepad(device, controllerId)
        connectedGamepads[device.id] = connectedGamepad
        Log.d(tag, "Gamepad connected(${device.id}) -> controller ${controllerId}")
        registerSensorsForDevice(device, connectedGamepad)
        logGamepadState("registered", device.id, controllerId)
    }

    /**
     * Releases the controller slot and motion sensors associated with the provided device id.
     */
    private fun unregisterGamepad(deviceId: Int) {
        val connected = connectedGamepads.remove(deviceId)
        connected?.let { unregisterSensorsForDevice(it) }
        AndroidControllerRegistry.releasePhysicalController(deviceId)
        Log.d(tag, "Gamepad disconnected(${deviceId})")
        val controllerId = connected?.controllerId ?: -1
        logGamepadState("released", deviceId, controllerId)
    }

    /**
     * Registers gyroscope and accelerometer sensors for motion-enabled controllers.
     */
    private fun registerSensorsForDevice(device: InputDevice, connectedGamepad: ConnectedGamepad) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        val sensorEventListener = sensorEventListeners[device.id] ?: MySensorEventListener(device.id)
        device.sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE).let {
            if(it.isNotEmpty()) {
                Log.i(tag, "register gyroscope listener : ${it[0].name}")
                device.sensorManager.registerListener(sensorEventListener, it[0], SensorManager.SENSOR_DELAY_GAME)
                sensorEventListeners.put(device.id, sensorEventListener)
                connectedGamepad.sensors.add(it[0])
            }
        }
        device.sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).let {
            if(it.isNotEmpty()) {
                Log.i(tag, "register accelerometer listener : ${it[0].name}")
                device.sensorManager.registerListener(sensorEventListener, it[0], SensorManager.SENSOR_DELAY_GAME)
                sensorEventListeners.put(device.id, sensorEventListener)
                connectedGamepad.sensors.add(it[0])
            }
        }
    }

    private fun unregisterSensorsForDevice(connectedGamepad: ConnectedGamepad) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        val listener = sensorEventListeners.remove(connectedGamepad.device.id) ?: return
        connectedGamepad.device.sensorManager.unregisterListener(listener)
        connectedGamepad.sensors.clear()
    }

    /**
     * Releases every registered controller and associated sensors so the system can rebuild from
     * scratch (e.g. when emulation restarts).
     */
    fun reset() {
        isSendSensor = false
        val deviceIds = connectedGamepads.keys.toList()
        for(deviceId in deviceIds) {
            unregisterGamepad(deviceId)
        }
        sensorEventListeners.clear()
    }

    /**
     * Logs the current controller pool state so we can trace how many devices are bound.
     */
    private fun logGamepadState(action: String, deviceId: Int, controllerId: Int) {
        try {
            val totalControllers = AndroidControllerRegistry.getConnectedControllerCount()
            Log.i(tag, "Gamepad $action (device=$deviceId, controller=$controllerId) -> total controllers: $totalControllers")
        } catch(ex: Throwable) {
            Log.e(tag, "Failed to query controller count after $action", ex)
        }
    }

    /**
     * Bridges Android motion sensor events to the LibKenjinx input layer.
     */
    inner class MySensorEventListener(val deviceId: Int): SensorEventListener {
        private var controllerId = -1

        override fun onSensorChanged(event: SensorEvent?) {
            if(isSendSensor && event != null) {
                if(controllerId == -1) {
                    controllerId = AndroidControllerRegistry.ensurePhysicalController(deviceId)
                    Log.d(tag, "sensor(${deviceId}) get controllerId:${controllerId}")
                }
                if(controllerId == -1) {
                    return
                }
                when(event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        val x = event.values[0]
                        val y = -event.values[2]
                        val z = event.values[1]
                        KenjinxNative.inputSetGyroData(x, y, z, controllerId)
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        val x = event.values[0]
                        val y = -event.values[2]
                        val z = event.values[1]
                        KenjinxNative.inputSetAccelerometerData(x, y, z, controllerId)
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }
    }
}
