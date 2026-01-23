package com.example.kioskpdv

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var configContainer: LinearLayout
    private lateinit var etServerUrl: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var sharedPreferences: SharedPreferences

    private var backButtonLongPressStartTime: Long = 0
    private val LONG_PRESS_DURATION = 2000 // 2 segundos para acionar o reset

    // Register callback for permission request
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue with notification setup if needed.
            } else {
                Toast.makeText(this, "Notificações são importantes para este App", Toast.LENGTH_SHORT).show()
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar Views
        webView = findViewById(R.id.webview)
        configContainer = findViewById(R.id.configContainer)
        etServerUrl = findViewById(R.id.etServerUrl)
        btnSave = findViewById(R.id.btnSave)

        sharedPreferences = getSharedPreferences("KioskPrefs", Context.MODE_PRIVATE)

        // Verificar Permissões (Android 13+)
        checkNotificationPermission()

        // Verificar se já temos URL salva
        val savedUrl = sharedPreferences.getString("SAVED_URL", null)

        if (savedUrl != null) {
            setupWebView(savedUrl)
        } else {
            showConfigScreen()
        }

        // Configurar botão de salvar
        btnSave.setOnClickListener {
            val url = etServerUrl.text.toString().trim()
            if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                sharedPreferences.edit().putString("SAVED_URL", url).apply()
                setupWebView(url)
            } else {
                etServerUrl.error = "URL inválida. Comece com http:// ou https://"
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showConfigScreen() {
        webView.visibility = View.GONE
        configContainer.visibility = View.VISIBLE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(url: String) {
        configContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        
        // Adicionar Ponte JS
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidApp")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Manter navegação no WebView
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Injetar JS para sobrescrever a API de Notificação do navegador
                injectNotificationPolyfill()
            }
        }

        webView.loadUrl(url)
    }

    private fun injectNotificationPolyfill() {
        val js = """
            if (!window.Notification) {
                window.Notification = function(title, options) {
                    this.title = title;
                    this.body = options ? options.body : '';
                    AndroidApp.showNotification(this.title, this.body);
                };
                window.Notification.permission = 'granted';
                window.Notification.requestPermission = function(callback) {
                    if(callback) callback('granted');
                    return Promise.resolve('granted');
                };
            } else {
                // Se o navegador já tem Notification, vamos TENTAR sobrescrever o construtor original 
                // ou fazer um 'hook' para chamar o nativo também, pois WebViews Android podem silenciar notificações HTML5.
                const OriginalNotification = window.Notification;
                window.Notification = function(title, options) {
                    // Chama a nativa Android
                    AndroidApp.showNotification(title, options ? options.body : '');
                    // Opcional: Chama a original se quiser manter comportamento padrão do site (console log etc)
                    // new OriginalNotification(title, options);
                }
                window.Notification.permission = 'granted';
                window.Notification.requestPermission = function(callback) {
                     if(callback) callback('granted');
                     return Promise.resolve('granted');
                };
            }
        """.trimIndent()
        
        webView.evaluateJavascript(js, null)
    }

    // Controle do Botão Voltar (Navegação interna ou Reset Long Press)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event?.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    backButtonLongPressStartTime = System.currentTimeMillis()
                } else if ((System.currentTimeMillis() - backButtonLongPressStartTime) >= LONG_PRESS_DURATION) {
                    // Long press detectado
                    showResetDialog()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Se foi um toque curto e não um long press consumido
            if ((System.currentTimeMillis() - backButtonLongPressStartTime) < LONG_PRESS_DURATION) {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun showResetDialog() {
        AlertDialog.Builder(this)
            .setTitle("Resetar Configuração")
            .setMessage(R.string.reset_config_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                sharedPreferences.edit().remove("SAVED_URL").apply()
                // Reiniciar a Activity para voltar ao estado inicial
                recreate()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
