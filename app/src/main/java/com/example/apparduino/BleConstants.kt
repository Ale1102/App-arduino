package com.example.apparduino



    object BleConstants {
    // El nombre que le pusiste a tu Arduino en el .ino
    const val DEVICE_NAME = "ArduinoMotorEAI"

    // UUIDs del Arduino (¡Copiados de tu .ino!)
    const val SERVICE_UUID = "19B10000-E8F2-537E-4F6C-D104768A1214"
    const val COMANDO_CHAR_UUID = "19B10001-E8F2-537E-4F6C-D104768A1214"
    const val DISTANCIA_CHAR_UUID = "19B10002-E8F2-537E-4F6C-D104768A1214"

    // Este UUID es estándar para habilitar notificaciones (Notify)
    const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"
}