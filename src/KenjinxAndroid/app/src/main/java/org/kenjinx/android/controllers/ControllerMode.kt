package org.kenjinx.android.controllers


/**
 * Runtime mode that describes how a physical controller should be exposed to the emulator.
 * Joy-Con specific modes encode whether the device is treated as a single horizontal pad or
 * participates in a combined pair.
 */
enum class ControllerMode {
    Unmatched,
    GenericMatched,
    JoyConSingle,
    JoyConPairedPrimary,
    JoyConPairedSecondary,
}
