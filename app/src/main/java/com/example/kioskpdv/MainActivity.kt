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
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.net.Uri
import android.content.Intent
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val TAG = "MainActivity"
    private var pendingPermissionRequest: PermissionRequest? = null
    private var mUploadMessage: ValueCallback<Array<Uri>>? = null
    private var mCameraPhotoPath: String? = null

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

    // Register callback for Camera permission request
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                pendingPermissionRequest?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            } else {
                pendingPermissionRequest?.deny()
                Toast.makeText(this, "Permissão de câmera necessária para esta função", Toast.LENGTH_SHORT).show()
            }
            pendingPermissionRequest = null
        }

    // Register callback for File Chooser (Camera)
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (mUploadMessage == null) return@registerForActivityResult

        var results: Array<Uri>? = null

        if (result.resultCode == RESULT_OK) {
            // Se tem foto da câmera
            if (mCameraPhotoPath != null) {
                val file = File(mCameraPhotoPath!!)
                if (file.exists()) {
                    results = arrayOf(Uri.fromFile(file))
                }
            }
            
            // Se o intent retornou dados (galeria)
            if (results == null && result.data != null && result.data?.dataString != null) {
                 results = arrayOf(Uri.parse(result.data?.dataString))
            }
        }

        mUploadMessage?.onReceiveValue(results)
        mUploadMessage = null
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

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                
                request.resources.forEach { resource ->
                    if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                        } else {
                            pendingPermissionRequest = request
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (mUploadMessage != null) {
                    mUploadMessage?.onReceiveValue(null)
                    mUploadMessage = null
                }
                mUploadMessage = filePathCallback

                var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent?.resolveActivity(packageManager) != null) {
                    // Create the File where the photo should go
                    var photoFile: File? = null
                    try {
                        photoFile = createImageFile()
                    } catch (ex: Exception) {
                        // Error occurring while creating the File
                    }
                    
                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        val photoURI: Uri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${applicationContext.packageName}.fileprovider",
                            photoFile
                        )
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    } else {
                        takePictureIntent = null
                    }
                }

                val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                contentSelectionIntent.type = "image/*"

                val intentArray: Array<Intent?> = if (takePictureIntent != null) {
                    arrayOf(takePictureIntent)
                } else {
                    arrayOfNulls(0)
                }

                val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Selecione ou Tire uma Foto")
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)

                fileChooserLauncher.launch(chooserIntent)
                return true
            }
        }
        
        // Adicionar Ponte JS
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidApp")
        
        // Integração Impressora Gertec
        val gertecPrinter = GertecPrinter(this)
        webView.addJavascriptInterface(AndroidPrinter(this, gertecPrinter), "AndroidPrinter")

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

    // Método chamado pela Interface JS (Trigger Genérico)
    fun printWebView() {
        val printManager = getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
        if (printManager != null) {
            val jobName = "${getString(R.string.app_name)} Tela"
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
        }
    }

    // [NOVO] Método para imprimir conteúdo HTML específico vindo do JS
    @SuppressLint("SetJavaScriptEnabled")
    fun printContent(htmlContent: String) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
        if (printManager == null) {
             Toast.makeText(this, "Erro: Serviço de Impressão indisponível.", Toast.LENGTH_LONG).show()
             return
        }

        // Criar um WebView "invisível" ou temporário para renderizar o ticket
        // Ele não precisa estar no layout se usarmos created context, mas é melhor adicionar e esconder
        // ou criar dinamicamente com application context.
        val printView = WebView(this)
        printView.settings.javaScriptEnabled = true
        
        printView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Quando terminar de renderizar o HTML, manda imprimir
                val jobName = "${getString(R.string.app_name)} Ticket"
                val printAdapter = printView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                
                // Cleanup (Remover referência ou limpar memória se possível, mas garbage collector cuida)
            }
        }

        // Carregar o conteúdo HTML
        printView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
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

    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            mCameraPhotoPath = absolutePath
        }
    }
}
