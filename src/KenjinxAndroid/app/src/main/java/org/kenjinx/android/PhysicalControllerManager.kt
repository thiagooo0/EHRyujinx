package org.kenjinx.android

import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.kenjinx.android.controllers.ControllerDeviceType
import org.kenjinx.android.controllers.ControllerMatchingManager
import org.kenjinx.android.controllers.ControllerMode
import org.kenjinx.android.controllers.ControllerRuntimeInfo
import org.kenjinx.android.viewmodels.QuickSettings
import kotlin.math.abs

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

class PhysicalControllerManager(val activity: MainActivity) {

    fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d("joycon", "resolveButtonId: $event")

        val device = event.device ?: return false
        if(!device.isGamepad) return false

        val runtime = ControllerMatchingManager.getRuntimeInfo(device.id) ?: return false
        val id = resolveButtonId(event.keyCode, runtime)
        if(id == GamePadButtonInputId.None) {
            return false
        }

        val isNotFallback = (event.flags and KeyEvent.FLAG_FALLBACK) == 0
        if(!isNotFallback) {
            return true
        }

        when(event.action) {
            KeyEvent.ACTION_UP -> KenjinxNative.inputSetButtonReleased(id.ordinal, runtime.controllerId)
            KeyEvent.ACTION_DOWN -> KenjinxNative.inputSetButtonPressed(id.ordinal, runtime.controllerId)
        }
        return true
    }

    fun onMotionEvent(ev: MotionEvent) {
        if(ev.action != MotionEvent.ACTION_MOVE) {
            return
        }
        val device = ev.device ?: return
        if(!device.isGamepad) return

        val runtime = ControllerMatchingManager.getRuntimeInfo(device.id) ?: return
        when(runtime.profile.mode) {
            ControllerMode.GenericMatched -> handleGenericMotion(ev, device, runtime.controllerId)
            ControllerMode.JoyConSingle -> handleJoyConSingleMotion(ev, device, runtime)
            ControllerMode.JoyConPairedPrimary -> handleJoyConPrimaryMotion(ev, device, runtime.controllerId)
            ControllerMode.JoyConPairedSecondary -> handleJoyConSecondaryMotion(ev, device, runtime.controllerId)
            ControllerMode.Unmatched -> {}
        }
    }

    private fun resolveButtonId(keyCode: Int, runtime: ControllerRuntimeInfo): GamePadButtonInputId {
        val quickSettings = QuickSettings(activity)
        val base = mapGenericButton(keyCode, quickSettings)
        Log.d("joycon", "resolveButtonId: $keyCode → $base, mode:${runtime.profile.mode}")
        return when(runtime.profile.mode) {
            ControllerMode.JoyConSingle -> mapJoyConSingleButton(runtime.profile.type, keyCode, base)
            ControllerMode.JoyConPairedSecondary -> mapJoyConPairedSecondaryButton(keyCode, base, quickSettings)
            else -> base
        }
    }

    private fun mapGenericButton(keycode: Int, quickSettings: QuickSettings): GamePadButtonInputId {
        return when(keycode) {
            KeyEvent.KEYCODE_BUTTON_A -> if(!quickSettings.useSwitchLayout) GamePadButtonInputId.A else GamePadButtonInputId.B
            KeyEvent.KEYCODE_BUTTON_B -> if(!quickSettings.useSwitchLayout) GamePadButtonInputId.B else GamePadButtonInputId.A
            KeyEvent.KEYCODE_BUTTON_X -> if(!quickSettings.useSwitchLayout) GamePadButtonInputId.X else GamePadButtonInputId.Y
            KeyEvent.KEYCODE_BUTTON_Y -> if(!quickSettings.useSwitchLayout) GamePadButtonInputId.Y else GamePadButtonInputId.X
            KeyEvent.KEYCODE_BUTTON_L1 -> GamePadButtonInputId.LeftShoulder
            KeyEvent.KEYCODE_BUTTON_L2 -> GamePadButtonInputId.LeftTrigger
            KeyEvent.KEYCODE_BUTTON_R1 -> GamePadButtonInputId.RightShoulder
            KeyEvent.KEYCODE_BUTTON_R2 -> GamePadButtonInputId.RightTrigger
            KeyEvent.KEYCODE_BUTTON_THUMBL -> GamePadButtonInputId.LeftStickButton
            KeyEvent.KEYCODE_BUTTON_THUMBR -> GamePadButtonInputId.RightStickButton
            KeyEvent.KEYCODE_BUTTON_11 -> GamePadButtonInputId.LeftStickButton
            KeyEvent.KEYCODE_BUTTON_12 -> GamePadButtonInputId.RightStickButton
            KeyEvent.KEYCODE_DPAD_UP -> GamePadButtonInputId.DpadUp
            KeyEvent.KEYCODE_DPAD_DOWN -> GamePadButtonInputId.DpadDown
            KeyEvent.KEYCODE_DPAD_LEFT -> GamePadButtonInputId.DpadLeft
            KeyEvent.KEYCODE_DPAD_RIGHT -> GamePadButtonInputId.DpadRight
            KeyEvent.KEYCODE_BUTTON_START -> GamePadButtonInputId.Plus
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamePadButtonInputId.Minus
            else -> GamePadButtonInputId.None
        }
    }

    private fun mapJoyConSingleButton(
        type: ControllerDeviceType,
        keycode: Int,
        base: GamePadButtonInputId
    ): GamePadButtonInputId {
        val result = when(type) {
            ControllerDeviceType.JoyConLeft -> when(keycode) {
                KeyEvent.KEYCODE_DPAD_UP -> GamePadButtonInputId.Y
                KeyEvent.KEYCODE_DPAD_DOWN -> GamePadButtonInputId.A
                KeyEvent.KEYCODE_DPAD_LEFT -> GamePadButtonInputId.B
                KeyEvent.KEYCODE_DPAD_RIGHT -> GamePadButtonInputId.X
                else -> when {
                    isJoyConSlKey(keycode) -> GamePadButtonInputId.LeftShoulder
                    isJoyConSrKey(keycode) -> GamePadButtonInputId.RightShoulder
                    else -> base
                }
            }
            ControllerDeviceType.JoyConRight -> when(keycode) {
                KeyEvent.KEYCODE_BUTTON_Y -> GamePadButtonInputId.A
                KeyEvent.KEYCODE_BUTTON_B -> GamePadButtonInputId.B
                KeyEvent.KEYCODE_BUTTON_X -> GamePadButtonInputId.X
                KeyEvent.KEYCODE_BUTTON_A -> GamePadButtonInputId.Y
                else -> when {
                    isJoyConSlKey(keycode) -> GamePadButtonInputId.LeftShoulder
                    isJoyConSrKey(keycode) -> GamePadButtonInputId.RightShoulder
                    else -> base
                }
            }
            else -> base
        }
        Log.d("joycon", "mapJoyConSingleButton $type , keycode:${keycode}, result:${result}")
        return result
    }

    private fun mapJoyConPairedSecondaryButton(
        keycode: Int,
        base: GamePadButtonInputId,
        quickSettings: QuickSettings
    ): GamePadButtonInputId {
        // Respect layout preference for ABXY when Joy-Con pair is used horizontally.
        return when(keycode) {
            KeyEvent.KEYCODE_BUTTON_A -> if(!quickSettings.useSwitchLayout) GamePadButtonInputId.A else GamePadButtonInputId.B
            KeyEvent.KEYCODE_BUTTON_B -> if(!quickSettings.useSwitchLayout) GamePadButtonInputId.B else GamePadButtonInputId.A
            KeyEvent.KEYCODE_BUTTON_X -> if(!quickSettings.useSwitchLayout) GamePadButtonInputId.X else GamePadButtonInputId.Y
            KeyEvent.KEYCODE_BUTTON_Y -> if(!quickSettings.useSwitchLayout) GamePadButtonInputId.Y else GamePadButtonInputId.X
            else -> base
        }
    }

    private fun handleGenericMotion(ev: MotionEvent, device: InputDevice, controllerId: Int) {
        val leftStickX = ev.getAxisValue(MotionEvent.AXIS_X)
        val leftStickY = ev.getAxisValue(MotionEvent.AXIS_Y)
        val rightStickX = ev.getAxisValue(MotionEvent.AXIS_Z)
        val rightStickY = ev.getAxisValue(MotionEvent.AXIS_RZ)

        KenjinxNative.inputSetStickAxis(1, leftStickX, -leftStickY, controllerId)
        KenjinxNative.inputSetStickAxis(2, rightStickX, -rightStickY, controllerId)

        handleHatDpad(ev, device, controllerId)
    }

    private fun handleJoyConPrimaryMotion(ev: MotionEvent, device: InputDevice, controllerId: Int) {
        val x = ev.axisValue(device, MotionEvent.AXIS_X)
        val y = ev.axisValue(device, MotionEvent.AXIS_Y)
        KenjinxNative.inputSetStickAxis(1, x, -y, controllerId)
        handleHatDpad(ev, device, controllerId)
    }

    private fun handleJoyConSecondaryMotion(ev: MotionEvent, device: InputDevice, controllerId: Int) {
        val x = ev.axisValue(device, MotionEvent.AXIS_Z, MotionEvent.AXIS_X)
        val y = ev.axisValue(device, MotionEvent.AXIS_RZ, MotionEvent.AXIS_Y)
        KenjinxNative.inputSetStickAxis(2, x, -y, controllerId)
    }

    private fun handleJoyConSingleMotion(ev: MotionEvent, device: InputDevice, runtime: ControllerRuntimeInfo) {
        val (rawX, rawY) = when(runtime.profile.type) {
            ControllerDeviceType.JoyConLeft -> {
                ev.axisValue(device, MotionEvent.AXIS_X) to ev.axisValue(device, MotionEvent.AXIS_Y)
            }
            ControllerDeviceType.JoyConRight -> {
                ev.axisValue(device, MotionEvent.AXIS_Z, MotionEvent.AXIS_X) to ev.axisValue(device, MotionEvent.AXIS_RZ, MotionEvent.AXIS_Y)
            }
            else -> ev.axisValue(device, MotionEvent.AXIS_X) to ev.axisValue(device, MotionEvent.AXIS_Y)
        }
        val (rotatedX, rotatedY) = when(runtime.profile.type) {
            ControllerDeviceType.JoyConLeft -> Pair(rawY, -rawX)
            ControllerDeviceType.JoyConRight -> Pair(-rawY, rawX)
            else -> Pair(rawX, rawY)
        }
        KenjinxNative.inputSetStickAxis(1, rotatedX, -rotatedY, runtime.controllerId)
    }

    private fun handleHatDpad(ev: MotionEvent, device: InputDevice, controllerId: Int) {
        if(device.sources and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) {
            return
        }
        val dPadHor = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
        val dPadVert = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)

        if(abs(dPadVert) < 0.01f) {
            KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadUp.ordinal, controllerId)
            KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadDown.ordinal, controllerId)
        }
        if(abs(dPadHor) < 0.01f) {
            KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadLeft.ordinal, controllerId)
            KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadRight.ordinal, controllerId)
        }
        if(dPadVert < 0.0f) {
            KenjinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadUp.ordinal, controllerId)
            KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadDown.ordinal, controllerId)
        }
        if(dPadHor < 0.0f) {
            KenjinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadLeft.ordinal, controllerId)
            KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadRight.ordinal, controllerId)
        }
        if(dPadVert > 0.0f) {
            KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadUp.ordinal, controllerId)
            KenjinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadDown.ordinal, controllerId)
        }
        if(dPadHor > 0.0f) {
            KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadLeft.ordinal, controllerId)
            KenjinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadRight.ordinal, controllerId)
        }
    }

    private fun MotionEvent.axisValue(device: InputDevice, vararg axes: Int): Float {
        for(axis in axes) {
            val range = device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK)
            if(range != null) {
                return getAxisValue(axis)
            }
        }
        return 0f
    }
}
