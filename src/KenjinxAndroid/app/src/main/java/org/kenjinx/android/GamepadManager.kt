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
    private var fallbackControllerId: Int = -1
    val sensorEventListener = MySensorEventListener()
    private var isSendSensor = false

    private val gamepads = mutableMapOf<Int, ConnectedGamepad>()

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

    fun setControllerId(id: Int) {
        fallbackControllerId = id
    }

    private fun checkForConnectedGamepads() {
        val deviceIds = inputManager.inputDeviceIds
        val seenDevices = mutableSetOf<Int>()
        for(deviceId in deviceIds) {
            InputDevice.getDevice(deviceId)?.let { device ->
                if(device.isGamepad) {
                    Log.d("GamepadManager", "Gamepad connected($deviceId)")
                    val controllerId = AndroidControllerRegistry.ensurePhysicalController(device.id)
                    val binding = gamepads.getOrPut(device.id) { ConnectedGamepad(device, controllerId) }
                    registerSensorsForDevice(binding)
                    Log.d("GamepadManager", "Gamepad connected: ${device.name} (controllerId=$controllerId)")
                    seenDevices.add(device.id)
                }
            }
        }

        val iterator = gamepads.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!seenDevices.contains(entry.key)) {
                unregisterSensors(entry.value)
                sensorEventListener.detachDevice(entry.key)
                AndroidControllerRegistry.releasePhysicalController(entry.key)
                iterator.remove()
            }
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        Log.d("GamepadManager", "Gamepad added: ${deviceId}")
        checkForConnectedGamepads()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        val removedDevice = gamepads[deviceId]
        removedDevice?.let {
            unregisterSensors(it)
            sensorEventListener.detachDevice(deviceId)
            gamepads.remove(deviceId)
            AndroidControllerRegistry.releasePhysicalController(deviceId)
            Log.d("GamepadManager", "Gamepad removed: ${it.device.name}")
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        Log.d("GamepadManager", "Gamepad changed: $deviceId")
        checkForConnectedGamepads()
    }

    private fun registerSensorsForDevice(binding: ConnectedGamepad) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        val device = binding.device
        val manager = device.sensorManager

        if (binding.sensors.none { it.type == Sensor.TYPE_GYROSCOPE }) {
            manager.getSensorList(Sensor.TYPE_GYROSCOPE).firstOrNull()?.let { sensor ->
                Log.i("GamepadManager", "register gyroscope listener : ${sensor.name}")
                manager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_GAME)
                sensorEventListener.bindSensor(device.id, sensor, binding.controllerId)
                binding.sensors.add(sensor)
            }
        }

        if (binding.sensors.none { it.type == Sensor.TYPE_ACCELEROMETER }) {
            manager.getSensorList(Sensor.TYPE_ACCELEROMETER).firstOrNull()?.let { sensor ->
                Log.i("GamepadManager", "register accelerometer listener : ${sensor.name}")
                manager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_GAME)
                sensorEventListener.bindSensor(device.id, sensor, binding.controllerId)
                binding.sensors.add(sensor)
            }
        }
    }

    private fun unregisterSensors(binding: ConnectedGamepad) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        val manager = binding.device.sensorManager
        binding.sensors.forEach { sensor ->
            manager.unregisterListener(sensorEventListener, sensor)
            sensorEventListener.unbindSensor(binding.device.id, sensor)
        }
        binding.sensors.clear()
    }

    fun reset() {
        isSendSensor = false
        fallbackControllerId = -1
        gamepads.values.forEach { unregisterSensors(it) }
        gamepads.clear()
        sensorEventListener.clear()
    }

    inner class MySensorEventListener(): SensorEventListener {
        private val sensorToController = mutableMapOf<Sensor, Int>()
        private val deviceSensors = mutableMapOf<Int, MutableList<Sensor>>()

        fun bindSensor(deviceId: Int, sensor: Sensor, controllerId: Int) {
            sensorToController[sensor] = controllerId
            val sensors = deviceSensors.getOrPut(deviceId) { mutableListOf() }
            sensors.add(sensor)
        }

        fun unbindSensor(deviceId: Int, sensor: Sensor) {
            sensorToController.remove(sensor)
            deviceSensors[deviceId]?.remove(sensor)
            if (deviceSensors[deviceId]?.isEmpty() == true) {
                deviceSensors.remove(deviceId)
            }
        }

        fun getControllerId(sensor: Sensor): Int? = sensorToController[sensor]

        fun clear() {
            sensorToController.clear()
            deviceSensors.clear()
        }

        fun detachDevice(deviceId: Int) {
            val sensors = deviceSensors.remove(deviceId) ?: return
            sensors.forEach { sensorToController.remove(it) }
        }

        override fun onSensorChanged(event: SensorEvent?) {
            if(isSendSensor && event != null) {
                val controllerId = getControllerId(event.sensor)
                    ?: if (fallbackControllerId != -1) fallbackControllerId else return

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
