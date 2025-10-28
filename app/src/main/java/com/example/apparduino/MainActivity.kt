package com.example.apparduino // ¡Asegúrate que sea tu nombre de paquete!

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.apparduino.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // ¡HEMOS QUITADO EL 'requestPermissionLauncher' DE AQUÍ!
    // ¡HEMOS QUITADO EL 'solicitarPermisosBLE()' DE AQUÍ!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Ya no se solicitan permisos al inicio. El fragmento lo hará.
    }
}