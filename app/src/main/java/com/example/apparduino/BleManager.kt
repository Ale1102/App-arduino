package com.example.apparduino

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

@SuppressLint("MissingPermission") // Los permisos se piden en ControlFragment
class BleManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val scanner by lazy { bluetoothAdapter?.bluetoothLeScanner }
    private var gatt: BluetoothGatt? = null

    // Características que encontraremos
    private var comandoCharacteristic: BluetoothGattCharacteristic? = null
    private var distanciaCharacteristic: BluetoothGattCharacteristic? = null

    // --- Flujos de Estado para la UI ---
    private val _estadoConexion = MutableStateFlow("Desconectado")
    val estadoConexion = _estadoConexion.asStateFlow()

    private val _distancia = MutableStateFlow(0.0f)
    val distancia = _distancia.asStateFlow()

    companion object {
        private const val TAG = "BleManager"
    }

    // --- 1. Escaneo ---
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            // Comprobamos el nombre del dispositivo desde BleConstants
            if (result.device.name == BleConstants.DEVICE_NAME) {
                Log.d(TAG, "Dispositivo encontrado: ${result.device.name}")
                _estadoConexion.value = "Encontrado. Conectando..."
                scanner?.stopScan(this)
                conectarAlDispositivo(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Error de escaneo: $errorCode")
            _estadoConexion.value = "Error de escaneo: $errorCode"
        }
    }


    fun iniciarEscaneo() {
        // --- INICIO DE LA CORRECCIÓN 1 (Permisos) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) y superior
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                _estadoConexion.value = "Error: Falta permiso BLUETOOTH_SCAN"
                Log.e(TAG, "Error: Falta permiso BLUETOOTH_SCAN")
                return
            }
        } else {
            // Android 11 (API 30) y anteriores
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                _estadoConexion.value = "Error: Faltan permisos de Bluetooth o Ubicación"
                Log.e(TAG, "Error: Faltan permisos de Bluetooth (manifest) o Ubicación (runtime)")
                return
            }
        }
        // --- FIN DE LA CORRECCIÓN 1 ---

        // --- INICIO DE LA CORRECCIÓN 2 (Duplicación) ---
        // Estas líneas solo deben estar aquí UNA VEZ.
        _estadoConexion.value = "Escaneando..."
        Log.d(TAG, "Iniciando escaneo...")
        scanner?.startScan(scanCallback)
        // --- FIN DE LA CORRECCIÓN 2 ---
    }

    // --- 2. Conexión y GATT Callback ---
    private fun conectarAlDispositivo(device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, gattCallback)
    }

    // --- INICIO DE LA CORRECCIÓN 3 (Lógica de conexión) ---
    // Este es el objeto que maneja TODOS los eventos de Bluetooth
    private val gattCallback = object : BluetoothGattCallback() {

        // SE EJECUTA CUANDO EL ESTADO DE CONEXIÓN CAMBIA
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

            // Primero, revisamos si la operación tuvo un error
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Error de conexión GATT. Status: $status")
                _estadoConexion.value = "Error al conectar (Status: $status)"
                desconectar() // Limpiamos la conexión fallida
                return // Salimos de la función
            }

            // Si status fue 0 (éxito), entonces revisamos el estado
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // ¡Conectado exitosamente!
                Log.d(TAG, "¡Conectado al dispositivo! Descubriendo servicios...")
                _estadoConexion.value = "Conectado. Descubriendo..."
                // Ahora que estamos conectados, descubrimos los servicios (oficinas)
                gatt.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Se desconectó
                Log.i(TAG, "Desconectado (status 0).")
                _estadoConexion.value = "Desconectado"
                desconectar() // Limpiar recursos
            }
        }

        // SE EJECUTA CUANDO SE DESCUBREN LOS SERVICIOS
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Servicios descubiertos.")
                val service = gatt.getService(UUID.fromString(BleConstants.SERVICE_UUID))
                if (service == null) {
                    Log.e(TAG, "Servicio ${BleConstants.SERVICE_UUID} no encontrado")
                    _estadoConexion.value = "Error: Servicio no encontrado"
                    return
                }

                // Buscar nuestras características (oficinas)
                comandoCharacteristic = service.getCharacteristic(UUID.fromString(BleConstants.COMANDO_CHAR_UUID))
                distanciaCharacteristic = service.getCharacteristic(UUID.fromString(BleConstants.DISTANCIA_CHAR_UUID))

                if (comandoCharacteristic != null && distanciaCharacteristic != null) {
                    Log.d(TAG, "Características encontradas. Habilitando notificaciones...")
                    _estadoConexion.value = "Conectado. Habilitando sensor..."
                    // ¡Éxito! Ahora encendemos las notificaciones del sensor
                    habilitarNotificacionesDistancia(gatt)
                } else {
                    Log.e(TAG, "Alguna característica no fue encontrada")
                    _estadoConexion.value = "Error: Característica no encontrada"
                }
            }
        }

        // SE EJECUTA CUANDO TERMINAMOS DE ESCRIBIR EL "INTERRUPTOR" DE NOTIFICACIÓN
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Notificaciones de distancia habilitadas.")
                _estadoConexion.value = "¡Conectado y Listo!"
            }
        }

        // SE EJECUTA CADA VEZ QUE EL ARDUINO ENVÍA UN NUEVO DATO DE DISTANCIA
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == UUID.fromString(BleConstants.DISTANCIA_CHAR_UUID)) {
                // El Arduino envía un Float (4 bytes)
                val dist = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                Log.d(TAG, "Nueva distancia recibida: $dist cm")
                _distancia.value = dist // Actualizar el StateFlow para la UI
            }
        }

        // SE EJECUTA CUANDO NUESTRO COMANDO (EJ. 'R') FUE ESCRITO
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Comando escrito exitosamente.")
            } else {
                Log.w(TAG, "Error al escribir comando: $status")
            }
        }
    }
    // --- FIN DEL BLOQUE gattCallback ---


    // Esta es una función "privada" de BleManager, NO es parte del gattCallback.)
    // Dentro de BleManager.kt...

    // 4. Habilitar Notificaciones (Leer datos del sensor)
    private fun habilitarNotificacionesDistancia(gatt: BluetoothGatt) {
        val characteristic = distanciaCharacteristic
        if (characteristic == null) {
            Log.e(TAG, "Error: Característica de distancia es nula")
            _estadoConexion.value = "Error: Caract. no hallada"
            return
        }

        // Paso 1: Decirle a Android que queremos recibir notificaciones
        Log.d(TAG, "Paso 1: setCharacteristicNotification(true)")
        gatt.setCharacteristicNotification(characteristic, true)

        // --- INICIO DE LA CORRECCIÓN DE PRUEBA ---
        // Algunos teléfonos necesitan una pequeña pausa aquí para procesar
        try {
            Thread.sleep(800) // 100ms de pausa
        } catch (e: InterruptedException) {
            // Ignorar
        }
        // --- FIN DE LA CORRECCIÓN DE PRUEBA ---

        // Paso 2: Escribir en el "interruptor" (Descriptor) para encenderlas
        val descriptor = characteristic.getDescriptor(UUID.fromString(BleConstants.CCC_DESCRIPTOR_UUID))
        if (descriptor == null) {
            Log.e(TAG, "Error: Descriptor CCC_DESCRIPTOR_UUID no encontrado")
            _estadoConexion.value = "Error: Descriptor no hallado"
            return
        }

        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        Log.d(TAG, "Paso 2: Escribiendo en el descriptor...")
        if (!gatt.writeDescriptor(descriptor)) {
            Log.e(TAG, "Error al iniciar escritura de descriptor")
            _estadoConexion.value = "Error al activar sensor"
        }
    }

    // --- 6. Enviar Comandos al Arduino ---
    fun enviarComando(comando: Char) {
        val characteristic = comandoCharacteristic
        if (gatt == null || characteristic == null) {
            Log.w(TAG, "No conectado o característica de comando no encontrada.")
            return
        }

        val comandoByte = comando.code.toByte()
        characteristic.value = byteArrayOf(comandoByte)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (!gatt!!.writeCharacteristic(characteristic)) {
            Log.e(TAG, "Fallo al iniciar escritura de comando")
        }
    }

    fun desconectar() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        comandoCharacteristic = null
        distanciaCharacteristic = null
        _estadoConexion.value = "Desconectado"
        _distancia.value = 0.0f
    }
}