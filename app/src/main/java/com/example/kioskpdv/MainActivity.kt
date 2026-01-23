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

        // Iniciar Serviço de Keep Alive (Background)
        val serviceIntent = android.content.Intent(this, KeepAliveService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

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
        webView.settings.databaseEnabled = true
        
        // Cache e Performance
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        
        // Conteúdo Misto (HTTP em HTTPS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Habilitar Alertas e Popups com Design Premium
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity, R.style.Theme_KioskPDV_Dialog_Divine)
                    .setTitle("Atenção")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity, R.style.Theme_KioskPDV_Dialog_Divine)
                    .setTitle("Confirmação")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("Confirmar") { _, _ -> result?.confirm() }
                    .setNegativeButton("Cancelar") { _, _ -> result?.cancel() }
                    .show()
                return true
            }
        }
        
        // Adicionar Ponte JS
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidApp")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Manter navegação no WebView
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectPolyfills()
            }
        }

        webView.loadUrl(url)
    }

    private fun injectPolyfills() {
        val js = """
            // Polyfill para Notificações
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
                 const OriginalNotification = window.Notification;
                 window.Notification = function(title, options) {
                     AndroidApp.showNotification(title, options ? options.body : '');
                 }
                 window.Notification.permission = 'granted';
                 window.Notification.requestPermission = function(callback) {
                      if(callback) callback('granted');
                      return Promise.resolve('granted');
                 };
            }

            // Polyfill para Impressão
            window.print = function() {
                AndroidApp.print();
            };
        """.trimIndent()
        
        webView.evaluateJavascript(js, null)
    }

    // Método chamado pela Interface JS
    fun printWebView() {
        val printManager = getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
        printManager?.let {
            val jobName = "${getString(R.string.app_name)} Document"
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            it.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
        }
    }

    // Bloquear botão voltar para modo Kiosk total (Opcional, mas solicitado 'fixo')
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Se o WebView puder voltar, volta. Se não, não faz nada (não fecha o app).
        if (webView.canGoBack()) {
            webView.goBack()
        }
        // super.onBackPressed() // Comentado para impedir fechamento
    }
}
