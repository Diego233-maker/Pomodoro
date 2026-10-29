package com.example.pomodorotimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat

/* ========================================================================= */
/* EXAMEN: CLASE FOREGROUND SERVICE (Servicio en Primer Plano)               */
/* Todo servicio tradicional en Android debe heredar de la clase Service.    */
/* ========================================================================= */
class PomodoroService : Service() {

    companion object {
        const val CHANNEL_ID = "pomodoro_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val BROADCAST_UPDATE = "POMODORO_UPDATE"
    }

    private var timer: CountDownTimer? = null
    private var FACTOR_TIEMPO = 60 * 1000L
    private var duracionConcentracionMs = 25 * FACTOR_TIEMPO
    private var duracionDescansoMs = 5 * FACTOR_TIEMPO
    private var tiempoRestanteMs = duracionConcentracionMs

    private var esFaseConcentracion = true
    private var estaPausado = false
    private var servicioActivo = false
    private var ciclosCompletados = 0

    override fun onCreate() {
        super.onCreate()
        // EXAMEN: Es obligatorio crear el canal de notificación antes de mostrarla (API 26+)
        crearCanalNotificacion()
    }

    /* ========================================================================= */
    /* EXAMEN: onStartCommand                                                    */
    /* Se ejecuta cada vez que la Actividad llama a startService() o             */
    /* startForegroundService(). Aquí recibimos las órdenes (Intents) y acciones.*/
    /* ========================================================================= */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val minConc = intent.getIntExtra("MIN_CONC", 25)
                val minDesc = intent.getIntExtra("MIN_DESC", 5)
                duracionConcentracionMs = minConc * FACTOR_TIEMPO
                duracionDescansoMs = minDesc * FACTOR_TIEMPO

                esFaseConcentracion = true
                tiempoRestanteMs = duracionConcentracionMs
                estaPausado = false
                servicioActivo = true

                /* ========================================================= */
                /* EXAMEN: startForeground()                                 */
                /* Este método es CRUCIAL. Pasa el servicio a Primer Plano   */
                /* mostrando la notificación obligatoria para que el sistema */
                /* operativo no mate el proceso en segundo plano.            */
                /* ========================================================= */
                startForeground(NOTIFICATION_ID, construirNotificacion())
                iniciarTemporizador(tiempoRestanteMs)
            }
            ACTION_PAUSE -> pausarTemporizador()
            ACTION_RESUME -> reanudarTemporizador()
            ACTION_STOP -> detenerServicio()
        }
        // START_STICKY indica que si el sistema mata el servicio por falta de memoria,
        // intentará reiniciarlo cuando sea posible.
        return START_STICKY
    }

    private fun iniciarTemporizador(milisegundos: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(milisegundos, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tiempoRestanteMs = millisUntilFinished
                actualizarNotificacion()
                enviarActualizacionUI()
            }

            override fun onFinish() {
                cambiarDeFase()
            }
        }.start()
    }

    private fun cambiarDeFase() {
        if (esFaseConcentracion) {
            ciclosCompletados++
            esFaseConcentracion = false
            tiempoRestanteMs = duracionDescansoMs
        } else {
            esFaseConcentracion = true
            tiempoRestanteMs = duracionConcentracionMs
        }
        iniciarTemporizador(tiempoRestanteMs)
        actualizarNotificacion()
        enviarActualizacionUI()
    }

    private fun pausarTemporizador() {
        timer?.cancel()
        estaPausado = true
        actualizarNotificacion()
        enviarActualizacionUI()
    }

    private fun reanudarTemporizador() {
        estaPausado = false
        iniciarTemporizador(tiempoRestanteMs)
        actualizarNotificacion()
        enviarActualizacionUI()
    }

    private fun detenerServicio() {
        timer?.cancel()
        servicioActivo = false
        estaPausado = false
        enviarActualizacionUI()
        // Remover la notificación en primer plano y detener el servicio
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /* ========================================================================= */
    /* EXAMEN: CONSTRUCCIÓN DE LA NOTIFICACIÓN                                   */
    /* Se usa NotificationCompat.Builder para asegurar compatibilidad.           */
    /* ========================================================================= */
    private fun construirNotificacion(): Notification {
        val estadoTexto = if (!esFaseConcentracion) "Descanso" else "Concentración"
        val estadoPausa = if (estaPausado) " (PAUSADO)" else ""
        val tiempoFormateado = formatearTiempo(tiempoRestanteMs)

        // PendingIntent para abrir la App al tocar la notificación
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent para el botón de Pausar/Reanudar dentro de la notificación
        val pauseResumeIntent = Intent(this, PomodoroService::class.java).apply {
            action = if (estaPausado) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePending = PendingIntent.getService(
            this, 1, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val textoBotonAccion = if (estaPausado) "Reanudar" else "Pausar"

        // PendingIntent para el botón de Detener dentro de la notificación
        val stopIntent = Intent(this, PomodoroService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Ciclo: $estadoTexto$estadoPausa")
            .setContentText("Restante: $tiempoFormateado | Ciclos completados: $ciclosCompletados")
            .setOngoing(true) // Hace que la notificación no se pueda deslizar para borrar (permanente)
            .setOnlyAlertOnce(true) // Evita sonido/vibración cada vez que se actualiza el cronómetro
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, textoBotonAccion, pauseResumePending)
            .addAction(android.R.drawable.ic_delete, "Detener", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun actualizarNotificacion() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, construirNotificacion())
    }

    private fun enviarActualizacionUI() {
        val intent = Intent(BROADCAST_UPDATE).apply {
            setPackage(packageName)
            putExtra("TIEMPO_STR", formatearTiempo(tiempoRestanteMs))
            putExtra("ES_CONCENTRACION", esFaseConcentracion)
            putExtra("ESTA_PAUSADO", estaPausado)
            putExtra("ACTIVO", servicioActivo)
            putExtra("CICLOS", ciclosCompletados)
        }
        sendBroadcast(intent)
    }

    private fun formatearTiempo(millis: Long): String {
        val totalSegundos = millis / 1000
        val minutos = totalSegundos / 60
        val segundos = totalSegundos % 60
        return String.format("%02d:%02d", minutos, segundos)
    }

    /* ========================================================================= */
    /* EXAMEN: NOTIFICATION CHANNEL (Canal de Notificaciones)                   */
    /* Obligatorio desde Android 8.0 (API 26) en adelante.                       */
    /* ========================================================================= */
    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                "Temporizador Pomodoro",
                NotificationManager.IMPORTANCE_LOW // IMPORTANCE_LOW evita sonidos molestos cada segundo al actualizar
            ).apply {
                description = "Muestra el progreso del ciclo Pomodoro en segundo plano"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    // onBind es obligatorio al heredar de Service, pero al ser un servicio "Started" (no Bound), retorna null.
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}