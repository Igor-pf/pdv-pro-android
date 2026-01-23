package com.example.kioskpdv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Interface Javascript injetada no WebView.
 * Permite que o site chame AndroidApp.showNotification(titulo, corpo)
 */
class WebAppInterface(private val mContext: Context) {

    @JavascriptInterface
    fun showNotification(title: String, body: String) {
        // Criar o canal de notificação (necessário para Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PDV Notifications"
            val descriptionText = "Notificações do Sistema PDV"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("PDV_CHANNEL_ID", name, importance).apply {
                description = descriptionText
            }
            // Registrar o canal
            val notificationManager: NotificationManager =
                mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Construir a notificação
        val builder = NotificationCompat.Builder(mContext, "PDV_CHANNEL_ID")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Ícone genérico, idealmente seria ic_notification
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Exibir
        try {
            val notificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // ID fixo ou aleatório para mostrar múltiplas? Usaremos System.currentTimeMillis
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            // Em tese a permissão já foi checada no MainActivity, mas por segurança.
            Toast.makeText(mContext, "Erro: Permissão de notificação negada.", Toast.LENGTH_SHORT).show()
        }
    }
}
