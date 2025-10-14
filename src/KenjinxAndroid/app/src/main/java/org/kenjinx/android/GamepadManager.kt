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
    private val inputManager: InputManager =
        context.getSystemService(Context.INPUT_SERVICE) as InputManager
    val sensorEventListeners = HashMap<Int, MySensorEventListener>()
    private var isSendSensor = false

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

    private fun checkForConnectedGamepads() {
        val deviceIds = inputManager.inputDeviceIds
        val seenDevices = mutableSetOf<Int>()
        for(deviceId in deviceIds) {
            InputDevice.getDevice(deviceId)?.let { device ->
                if(device.isGamepad) {
                    Log.d("GamepadManager", "Gamepad connected($deviceId)")
                    registerSensorsForDevice(device)
                    seenDevices.add(device.id)
                }
            }
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        Log.d("GamepadManager", "Gamepad added: ${deviceId}")
        checkForConnectedGamepads()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        checkForConnectedGamepads()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        Log.d("GamepadManager", "Gamepad changed: $deviceId")
        checkForConnectedGamepads()
    }

    private fun registerSensorsForDevice(device: InputDevice) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        val sensorEventListener = sensorEventListeners[device.id] ?: MySensorEventListener(device.id)
        device.sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE).let {
            if(it.isNotEmpty()) {
                Log.i("GamepadManager", "register gyroscope listener : ${it[0].name}")
                device.sensorManager.registerListener(sensorEventListener, it[0], SensorManager.SENSOR_DELAY_GAME)
                sensorEventListeners.put(device.id, sensorEventListener)
            }
        }
        device.sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).let {
            if(it.isNotEmpty()) {
                Log.i("GamepadManager", "register accelerometer listener : ${it[0].name}")
                device.sensorManager.registerListener(sensorEventListener, it[0], SensorManager.SENSOR_DELAY_GAME)
                sensorEventListeners.put(device.id, sensorEventListener)
            }
        }
    }

    fun reset() {
        isSendSensor = false
        sensorEventListeners.clear()
    }

    inner class MySensorEventListener(val deviceId: Int): SensorEventListener {
        private var controllerId = -1

        override fun onSensorChanged(event: SensorEvent?) {
            if(isSendSensor && event != null) {
                if(controllerId == -1) {
                    controllerId = AndroidControllerRegistry.ensurePhysicalController(deviceId)
                    Log.d("sensor", "sensor(${deviceId}) get controllerId:${controllerId}")
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
