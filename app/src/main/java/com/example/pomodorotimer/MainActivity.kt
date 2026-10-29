package com.example.pomodorotimer
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast


class MainActivity : AppCompatActivity() {
    private lateinit var tvEstadoCiclo: TextView
    private lateinit var tvTiempoRestante: TextView
    private lateinit var tvCiclosCompletados: TextView
    private lateinit var etTiempoConcentracion: EditText
    private lateinit var etTiempoDescanso: EditText
    private lateinit var btnIniciar: Button
    private lateinit var btnPausarReanudar: Button
    private lateinit var btnDetener: Button
    private var estaPausado = false
    private val receptorActualizaciones = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val activo = intent.getBooleanExtra("ACTIVO", false)
            val tiempoStr = intent.getStringExtra("TIEMPO_STR") ?: "25:00"
            val esConcentracion = intent.getBooleanExtra("ES_CONCENTRACION", true)
            estaPausado = intent.getBooleanExtra("ESTA_PAUSADO", false)
            val ciclos = intent.getIntExtra("CICLOS", 0)

            tvCiclosCompletados.text = "Ciclos completados: $ciclos"

            if (!activo) {
                tvEstadoCiclo.text = "SESIÓN DETENIDA"
                tvTiempoRestante.text = "00:00"
                btnIniciar.isEnabled = true
                btnPausarReanudar.isEnabled = false
                btnDetener.isEnabled = false
                etTiempoConcentracion.isEnabled = true
                etTiempoDescanso.isEnabled = true
                return
            }

            tvTiempoRestante.text = tiempoStr
            val fase = if (esConcentracion) "CONCENTRACIÓN" else "DESCANSO"
            tvEstadoCiclo.text = if (estaPausado) "$fase (PAUSADO)" else "EN CURSO: $fase"

            btnIniciar.isEnabled = false
            btnPausarReanudar.isEnabled = true
            btnDetener.isEnabled = true
            etTiempoConcentracion.isEnabled = false
            etTiempoDescanso.isEnabled = false

            btnPausarReanudar.text = if (estaPausado) "Reanudar" else "Pausar"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        pedirPermisoNotificaciones()
        tvEstadoCiclo = findViewById(R.id.tvEstadoCiclo)
        tvTiempoRestante = findViewById(R.id.tvTiempoRestante)
        tvCiclosCompletados = findViewById(R.id.tvCiclosCompletados)
        etTiempoConcentracion = findViewById(R.id.etTiempoConcentracion)
        etTiempoDescanso = findViewById(R.id.etTiempoDescanso)
        btnIniciar = findViewById(R.id.btnIniciar)
        btnPausarReanudar = findViewById(R.id.btnPausarReanudar)
        btnDetener = findViewById(R.id.btnDetener)
        btnIniciar.setOnClickListener {
            val minConc = etTiempoConcentracion.text.toString().toIntOrNull() ?: 25
            val minDesc = etTiempoDescanso.text.toString().toIntOrNull() ?: 5

            if (minConc <= 0 || minDesc <= 0) {
                Toast.makeText(this, "Ingresa valores mayores a 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, PomodoroService::class.java).apply {
                action = PomodoroService.ACTION_START
                putExtra("MIN_CONC", minConc)
                putExtra("MIN_DESC", minDesc)
            }
            ContextCompat.startForegroundService(this, intent)
        }

        btnPausarReanudar.setOnClickListener {
            val accion = if (estaPausado) PomodoroService.ACTION_RESUME else PomodoroService.ACTION_PAUSE
            val intent = Intent(this, PomodoroService::class.java).apply {
                action = accion
            }
            startService(intent)
        }

        btnDetener.setOnClickListener {
            val intent = Intent(this, PomodoroService::class.java).apply {
                action = PomodoroService.ACTION_STOP
            }
            startService(intent)
        }

    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(PomodoroService.BROADCAST_UPDATE)
        ContextCompat.registerReceiver(
            this,
            receptorActualizaciones,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    override fun onStop() {
        super.onStop()
        unregisterReceiver(receptorActualizaciones)
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
