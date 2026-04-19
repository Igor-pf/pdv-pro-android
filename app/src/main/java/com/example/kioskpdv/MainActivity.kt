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
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
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

    private lateinit var geckoView: GeckoView
    private val geckoSession = GeckoSession()
    private var geckoRuntime: GeckoRuntime? = null
    
    private val TAG = "MainActivity"
    private var mUploadMessage: Any? = null // Placeholder se precisar de upload no futuro no Gecko
    private var mCameraPhotoPath: String? = null

    private lateinit var configContainer: LinearLayout
    private lateinit var etServerUrl: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var sharedPreferences: SharedPreferences
    
    // Ponte de Mensagens para GeckoView
    private lateinit var webAppInterface: WebAppInterface
    private lateinit var androidPrinter: AndroidPrinter

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

    // Register callback for QR Code Scanner
    private val qrCodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val qrCode = result.data?.getStringExtra("SCAN_RESULT")
            if (qrCode != null) {
                val js = "javascript:onQRCodeScanned('$qrCode')"
                geckoSession.loadUri(js)
                Toast.makeText(this, "QR Code Lido: $qrCode", Toast.LENGTH_SHORT).show()
            }
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
        geckoView = findViewById(R.id.geckoview)
        configContainer = findViewById(R.id.configContainer)
        etServerUrl = findViewById(R.id.etServerUrl)
        btnSave = findViewById(R.id.btnSave)

        sharedPreferences = getSharedPreferences("KioskPrefs", Context.MODE_PRIVATE)

        // Inicializar Interfaces
        val gertecPrinter = GertecPrinter(this)
        webAppInterface = WebAppInterface(this)
        androidPrinter = AndroidPrinter(this, gertecPrinter)

        // Verificar Permissões (Android 13+)
        checkNotificationPermission()

        // Verificar se já temos URL salva
        val savedUrl = sharedPreferences.getString("SAVED_URL", null)

        if (savedUrl != null) {
            setupGeckoView(savedUrl)
        } else {
            showConfigScreen()
        }

        // Configurar botão de salvar
        btnSave.setOnClickListener {
            val url = etServerUrl.text.toString().trim()
            if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                sharedPreferences.edit().putString("SAVED_URL", url).apply()
                setupGeckoView(url)
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
        geckoView.visibility = View.GONE
        configContainer.visibility = View.VISIBLE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGeckoView(url: String) {
        configContainer.visibility = View.GONE
        geckoView.visibility = View.VISIBLE

        if (geckoRuntime == null) {
            geckoRuntime = GeckoRuntime.create(this)
        }

        // Configurar Delegado de Prompts (Alertas/Confirmações)
        geckoSession.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Atenção")
                    .setMessage(prompt.message)
                    .setPositiveButton("OK") { _, _ ->  }
                    .show()
                // Usamos dismiss() para alertas simples, pois confirm() pode estar protegido em algumas builds
                return GeckoResult.fromValue(prompt.dismiss())
            }
        }

        // Configurar Navegação (Opcional - Removido override com erro de assinatura)
        geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
             // override fun onLocationChange...
        }

        // CARREGAR WEB EXTENSION (MENSAGERIA)
        geckoRuntime?.webExtensionController?.ensureBuiltIn("resource://android/assets/messaging-extension/", "messaging@pdv.pro")
            ?.accept(
                { extension ->
                    extension?.setMessageDelegate(object : WebExtension.MessageDelegate {
                        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                            handleBridgeMessage(message)
                            return null
                        }
                    }, "browser")
                },
                { e -> android.util.Log.e("GeckoView", "Erro ao carregar extensão", e) }
            )

        geckoView.setSession(geckoSession)
        geckoSession.loadUri(url)
    }

    private fun handleBridgeMessage(message: Any) {
        try {
            val json = com.google.gson.Gson().toJson(message)
            val jsonObj = com.google.gson.JsonObject()
            val data = com.google.gson.JsonParser.parseString(json).asJsonObject
            
            val bridge = data.get("bridge")?.asString
            val method = data.get("method")?.asString
            val params = data.getAsJsonArray("params")

            runOnUiThread {
                when (bridge) {
                    "AndroidApp" -> handleAppMethod(method, params)
                    "AndroidPrinter" -> handlePrinterMethod(method, params)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GeckoView", "Erro ao processar mensagem da ponte", e)
        }
    }

    private fun handleAppMethod(method: String?, params: com.google.gson.JsonArray) {
        when (method) {
            "showNotification" -> webAppInterface.showNotification(params[0].asString, params[1].asString)
            "print" -> printWebView()
            "printHtml" -> printContent(params[0].asString)
            "scanQRCode" -> launchQRScanner()
        }
    }

    private fun handlePrinterMethod(method: String?, params: com.google.gson.JsonArray) {
        when (method) {
            "imprimir" -> androidPrinter.imprimir(params[0].asString)
            "printList" -> androidPrinter.printList(params[0].asString)
        }
    }

    private fun injectPolyfills() {
        // Agora injetado via WebExtension content.js para ser síncrono e robusto
    }

    // Método chamado pela Interface JS (Trigger Genérico)
    fun printWebView() {
        val printManager = getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
        if (printManager != null) {
            val jobName = "${getString(R.string.app_name)} Tela"
            // GeckoView requer uma estratégia diferente para impressão direta (PDF generation)
            // Por enquanto, mostraremos um aviso se a impressão nativa da Web for acionada
            Toast.makeText(this, "Impressão de Tela não disponível via GeckoDirect. Use Impressão de Ticket.", Toast.LENGTH_SHORT).show()
        }
    }

    // [NOVO] Método para imprimir conteúdo HTML específico vindo do JS
    fun printContent(htmlContent: String) {
        // Para GeckoView, a impressão de conteúdo HTML específico via serviço de impressão do Android
        // requer a geração de um PDF ou o uso de uma aba invisível que suporte impressão.
        // Como o foco é a maquininha Gertec, a impressão de tickets deve ser feita via AndroidPrinter.
        Toast.makeText(this, "Impressão via Sistema desabilitada no GeckoView. Use a integração nativa Gertec.", Toast.LENGTH_LONG).show()
    }

    // Bloquear botão voltar para modo Kiosk total (Opcional, mas solicitado 'fixo')
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Se puder voltar no GeckoView
        geckoSession.goBack()
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

    fun launchQRScanner() {
        val intent = Intent(this, QRScanActivity::class.java)
        qrCodeLauncher.launch(intent)
    }
}
