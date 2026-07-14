package com.example.kioskpdv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY garante que o serviço seja recriado se o sistema matar por falta de memória
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "PDV_BACKGROUND_SERVICE"
        val channelName = "PDV Serviço em Segundo Plano"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("PDV Ativo")
            .setContentText("O sistema PDV está rodando em segundo plano para receber notificações.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details) // Pode substituir pelo ic_launcher se preferir
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        // ID do serviço deve ser > 0
        startForeground(1, notification)
    }
}
