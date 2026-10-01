package com.example.pcremote

/**
 * The HID report descriptor tells Windows what kind of device we are.
 * Windows reads it once while pairing, then knows how to decode our reports.
 *
 *   Report 1 = keyboard  -> 8 bytes  [modifiers, reserved, key1..key6]
 *   Report 2 = mouse     -> 4 bytes  [buttons, dx, dy, wheel]
 *   Report 3 = media     -> 2 bytes  [usage low byte, usage high byte]
 */
object HidDescriptor {
    const val ID_KEYBOARD = 1
    const val ID_MOUSE = 2
    const val ID_MEDIA = 3

    val DESCRIPTOR: ByteArray = intArrayOf(
        // ---------------- Keyboard (Report ID 1) ----------------
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x06,        // Usage (Keyboard)
        0xA1, 0x01,        // Collection (Application)
        0x85, ID_KEYBOARD, //   Report ID (1)
        0x05, 0x07,        //   Usage Page (Key Codes)
        0x19, 0xE0,        //   Usage Minimum (Left Ctrl)
        0x29, 0xE7,        //   Usage Maximum (Right GUI)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x01,        //   Logical Maximum (1)
        0x75, 0x01,        //   Report Size (1 bit)
        0x95, 0x08,        //   Report Count (8)      -> modifier byte
        0x81, 0x02,        //   Input (Data, Variable, Absolute)
        0x95, 0x01,        //   Report Count (1)
        0x75, 0x08,        //   Report Size (8)
        0x81, 0x01,        //   Input (Constant)      -> reserved byte
        0x95, 0x06,        //   Report Count (6)
        0x75, 0x08,        //   Report Size (8)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x65,        //   Logical Maximum (101)
        0x05, 0x07,        //   Usage Page (Key Codes)
        0x19, 0x00,        //   Usage Minimum (0)
        0x29, 0x65,        //   Usage Maximum (101)
        0x81, 0x00,        //   Input (Data, Array)   -> up to 6 pressed keys
        0xC0,              // End Collection

        // ---------------- Mouse (Report ID 2) ----------------
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x02,        // Usage (Mouse)
        0xA1, 0x01,        // Collection (Application)
        0x85, ID_MOUSE,    //   Report ID (2)
        0x09, 0x01,        //   Usage (Pointer)
        0xA1, 0x00,        //   Collection (Physical)
        0x05, 0x09,        //     Usage Page (Buttons)
        0x19, 0x01,        //     Usage Minimum (Button 1)
        0x29, 0x03,        //     Usage Maximum (Button 3)
        0x15, 0x00,        //     Logical Minimum (0)
        0x25, 0x01,        //     Logical Maximum (1)
        0x95, 0x03,        //     Report Count (3)
        0x75, 0x01,        //     Report Size (1)
        0x81, 0x02,        //     Input (Data, Variable, Absolute) -> 3 button bits
        0x95, 0x01,        //     Report Count (1)
        0x75, 0x05,        //     Report Size (5)
        0x81, 0x01,        //     Input (Constant)                 -> 5 padding bits
        0x05, 0x01,        //     Usage Page (Generic Desktop)
        0x09, 0x30,        //     Usage (X)
        0x09, 0x31,        //     Usage (Y)
        0x09, 0x38,        //     Usage (Wheel)
        0x15, 0x81,        //     Logical Minimum (-127)
        0x25, 0x7F,        //     Logical Maximum (127)
        0x75, 0x08,        //     Report Size (8)
        0x95, 0x03,        //     Report Count (3)
        0x81, 0x06,        //     Input (Data, Variable, Relative) -> dx, dy, wheel
        0xC0,              //   End Collection
        0xC0,              // End Collection

        // ---------------- Media keys (Report ID 3) ----------------
        0x05, 0x0C,        // Usage Page (Consumer)
        0x09, 0x01,        // Usage (Consumer Control)
        0xA1, 0x01,        // Collection (Application)
        0x85, ID_MEDIA,    //   Report ID (3)
        0x15, 0x00,        //   Logical Minimum (0)
        0x26, 0xFF, 0x03,  //   Logical Maximum (1023)
        0x19, 0x00,        //   Usage Minimum (0)
        0x2A, 0xFF, 0x03,  //   Usage Maximum (1023)
        0x75, 0x10,        //   Report Size (16)
        0x95, 0x01,        //   Report Count (1)
        0x81, 0x00,        //   Input (Data, Array, Absolute)
        0xC0               // End Collection
    ).map { it.toByte() }.toByteArray()
}
