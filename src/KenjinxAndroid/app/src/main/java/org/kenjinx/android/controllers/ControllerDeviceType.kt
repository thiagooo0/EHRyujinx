package org.kenjinx.android.controllers

/**
 *  Created by GuoShaoHong on 2025/10/17!!!
 */
/**
 * High level classification of a physical controller that can be matched inside the
 * Android UI. We currently distinguish between generic pads and the two Joy-Con halves.
 */
enum class ControllerDeviceType {
    Generic,
    JoyConLeft,
    JoyConRight,
}
