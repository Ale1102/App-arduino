package com.example.apparduino



import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager(application)

    // Exponer los estados del BleManager a la UI
    val estadoConexion = bleManager.estadoConexion
    val distancia = bleManager.distancia

    // Estado para el Switch de modo automático
    private val _modoAutomatico = MutableStateFlow(false)
    val modoAutomatico = _modoAutomatico.asStateFlow()

    fun iniciarConexion() {
        bleManager.iniciarEscaneo()
    }

    fun desconectar() {
        bleManager.desconectar()
    }

    fun enviarComandoMotor(comando: Char) {
        // Solo enviar comandos de motor si NO estamos en modo automático
        if (!_modoAutomatico.value) {
            bleManager.enviarComando(comando)
        }
    }

    fun setModoAutomatico(activado: Boolean) {
        _modoAutomatico.value = activado
        if (activado) {
            bleManager.enviarComando('A') // Comando para modo Automático
        } else {
            bleManager.enviarComando('M') // Comando para modo Manual
        }
    }
}