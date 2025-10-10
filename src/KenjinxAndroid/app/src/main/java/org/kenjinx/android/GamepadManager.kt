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
    private var controllerId: Int = -1
    val sensorEventListener = MySensorEventListener()
    private var isSendSensor = false

    val gamepads = mutableListOf<InputDevice>()

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

    fun setControllerId(id: Int) {
        controllerId = id
    }

    private fun checkForConnectedGamepads() {
        gamepads.clear()
        val deviceIds = inputManager.inputDeviceIds
        for(deviceId in deviceIds) {
            InputDevice.getDevice(deviceId)?.let { device ->
                if(device.isGamepad) {
                    Log.d("GamepadManager", "Gamepad connected($deviceId)")
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        device.sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE).let {
                            if(it.isNotEmpty()) {
                                Log.i("GamepadManager", "register gyroscope listener : ${it[0].name}")

                                device.sensorManager.registerListener(sensorEventListener, it[0], SensorManager.SENSOR_DELAY_GAME)
                            }
                        }
                        device.sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).let {
                            if(it.isNotEmpty()) {
                                Log.i("GamepadManager", "register accelerometer listener : ${it[0].name}")
                                device.sensorManager.registerListener(sensorEventListener, it[0], SensorManager.SENSOR_DELAY_GAME)
                            }
                        }
                    }

                    gamepads.add(device)
                    Log.d("GamepadManager", "Gamepad connected: ${device.name}")
                }
            }
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        Log.d("GamepadManager", "Gamepad added: ${deviceId}")
        checkForConnectedGamepads()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        val removedDevice = gamepads.find { it.id == deviceId }
        removedDevice?.let {
            gamepads.remove(it)
            Log.d("GamepadManager", "Gamepad removed: ${it.name}")
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        Log.d("GamepadManager", "Gamepad changed: $deviceId")
        checkForConnectedGamepads()
    }

    inner class MySensorEventListener(): SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if(isSendSensor) {
                when(event?.sensor?.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        val x = event.values[0]
                        val y = -event.values[2]
                        val z = event.values[1]
                        KenjinxNative.inputSetGyroData(
                            x,
                            y,
                            z,
                            controllerId
                        )
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
