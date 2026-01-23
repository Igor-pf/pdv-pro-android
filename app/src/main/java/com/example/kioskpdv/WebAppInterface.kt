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
        val soundUri = android.net.Uri.parse("android.resource://" + mContext.packageName + "/" + R.raw.notificacao)

        // Criar o canal de notificação (necessário para Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PDV Notifications"
            val descriptionText = "Notificações do Sistema PDV"
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            // Usar ID Novo para garantir que o Android registre o novo som
            val channel = NotificationChannel("PDV_CHANNEL_ID_CUSTOM_SOUND", name, importance).apply {
                description = descriptionText
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            // Registrar o canal
            val notificationManager: NotificationManager =
                mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Construir a notificação
        val builder = NotificationCompat.Builder(mContext, "PDV_CHANNEL_ID_CUSTOM_SOUND")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
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

    @JavascriptInterface
    fun print() {
        if (mContext is MainActivity) {
            // Executar na Thread Principal
            mContext.runOnUiThread {
                mContext.printWebView()
            }
        }
    }

    @JavascriptInterface
    fun printHtml(html: String) {
        if (mContext is MainActivity) {
            mContext.runOnUiThread {
                Toast.makeText(mContext, "Imprimindo Ticket...", Toast.LENGTH_SHORT).show()
                mContext.printContent(html)
            }
        }
    }
        }
    }
}
