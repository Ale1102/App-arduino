package com.example.apparduino

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.apparduino.databinding.FragmentControlBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ControlFragment : Fragment() {

    private var _binding: FragmentControlBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    // --- INICIO DE LA CORRECCIÓN 1 ---
    // 1. Mover el "lanzador" de permisos de MainActivity aquí
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) allGranted = false
            }

            if (allGranted) {
                // ¡Permisos concedidos! Ahora sí, conectar.
                Log.d("ControlFragment", "Permisos concedidos por el usuario. Iniciando conexión.")
                viewModel.iniciarConexion()
            } else {
                // El usuario denegó los permisos
                Log.w("ControlFragment", "Permisos denegados por el usuario.")
                binding.tvEstado.text = "Error: Permisos denegados"
                Toast.makeText(requireContext(), "Se requieren permisos de Bluetooth y Ubicación para escanear.", Toast.LENGTH_LONG).show()
            }
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        // --- INICIO DE LA CORRECCIÓN 2 ---
        // 2. Hacer que el botón "Conectar" verifique los permisos
        binding.btnConectar.setOnClickListener {
            if (viewModel.estadoConexion.value.startsWith("Conectado")) {
                viewModel.desconectar()
            } else {
                // Verificar permisos ANTES de intentar conectar
                if (checkPermissions()) {
                    // Si ya los tenemos, conectar directamente
                    Log.d("ControlFragment", "Permisos ya concedidos. Iniciando conexión.")
                    viewModel.iniciarConexion()
                } else {
                    // Si no los tenemos, lanzar el pop-up para pedirlos
                    Log.d("ControlFragment", "Permisos no concedidos. Solicitando...")
                    solicitarPermisosBLE()
                }
            }
        }
        // --- FIN DE LA CORRECCIÓN 2 ---

        // Botones de Motor
        binding.btnIzquierda.setOnClickListener { viewModel.enviarComandoMotor('L') }
        binding.btnDetener.setOnClickListener { viewModel.enviarComandoMotor('S') }
        binding.btnDerecha.setOnClickListener { viewModel.enviarComandoMotor('R') }

        // Switch de Modo Automático
        binding.switchModoAuto.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setModoAutomatico(isChecked)
        }
    }

    private fun observeViewModel() {

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.estadoConexion.collectLatest { estado ->
                binding.tvEstado.text = "Estado: $estado"

                // --- INICIO DE LA CORRECCIÓN ---
                // Ahora comprueba "Conectado..." O "¡Conectado y Listo!"
                val conectado = estado.startsWith("Conectado") || estado == "¡Conectado y Listo!"
                val escaneando = estado.startsWith("Escaneando") || estado.startsWith("Conectando")
                // --- FIN DE LA CORRECCIÓN ---

                // 1. Actualizar texto del botón
                binding.btnConectar.text = if (conectado) "Desconectar" else "Conectar"

                // 2. Deshabilitar el botón si está escaneando/conectando
                binding.btnConectar.isEnabled = !escaneando

                // 3. Actualizar color
                val colorRes: Int
                if (conectado) {
                    // Rojo si está conectado (para desconectar)
                    colorRes = android.R.color.holo_red_dark
                } else if (escaneando) {
                    // Gris si está trabajando
                    colorRes = android.R.color.darker_gray
                } else  {
                    // Azul si está listo para conectar
                    colorRes = android.R.color.holo_blue_dark
                }
                binding.btnConectar.setBackgroundColor(ContextCompat.getColor(requireContext(), colorRes))

                // 4. Habilitar/deshabilitar switch (solo si estamos conectados)
                binding.switchModoAuto.isEnabled = conectado // <-- Ahora esto funcionará
            }
        }


        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.distancia.collectLatest { dist ->
                binding.tvDistancia.text = "%.1f cm".format(dist)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.modoAutomatico.collectLatest { automatico ->
                if (binding.switchModoAuto.isChecked != automatico) {
                    binding.switchModoAuto.isChecked = automatico
                }
                binding.btnIzquierda.isEnabled = !automatico
                binding.btnDetener.isEnabled = !automatico
                binding.btnDerecha.isEnabled = !automatico
            }
        }
    }



    private fun checkPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Se necesitan todos estos en runtime
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            // Android 11 e inferiores: BLUETOOTH y ADMIN son del manifest.
            // SOLO necesitamos verificar la UBICACIÓN en runtime.
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun solicitarPermisosBLE() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Pedir todos
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            // Android 11 e inferiores: Pedir SOLO UBICACIÓN.
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        // Lanzar el pop-up de permisos
        requestPermissionLauncher.launch(permissionsToRequest)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}