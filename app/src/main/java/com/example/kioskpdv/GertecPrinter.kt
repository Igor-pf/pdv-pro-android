package com.example.kioskpdv

import android.content.Context
import android.util.Log
import android.widget.Toast
import br.com.gertec.easylayer.printer.Alignment
import br.com.gertec.easylayer.printer.Printer
import br.com.gertec.easylayer.printer.PrinterError
import br.com.gertec.easylayer.printer.PrinterException
import br.com.gertec.easylayer.printer.TextFormat

class GertecPrinter(private val context: Context) : Printer.Listener {

    private var printer: Printer? = null
    private val TAG = "GertecPrinter"

    init {
        try {
            // Inicializa a impressora usando o Singleton do EasyLayer
            printer = Printer.getInstance(context, this)
            Log.d(TAG, "EasyLayer Printer inicializado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar EasyLayer: ${e.message}")
            e.printStackTrace()
        }
    }

    fun printText(text: String) {
        try {
            if (printer == null) {
                printer = Printer.getInstance(context, this)
            }

            val textFormat = TextFormat()
            textFormat.setAlignment(Alignment.LEFT)
            textFormat.setFontSize(22)
            textFormat.setLineSpacing(5)

            printer?.printText(textFormat, text)
            // Removido scrollPaper forçado aqui para evitar desperdício. 
            // O JS deve enviar comandos de quebra se precisar.
            
            Log.d(TAG, "Comando de impressão simples enviado")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao imprimir: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Imprime uma lista de comandos formatados em JSON
     * Exemplo: [{"text":"Titulo", "size":30, "align":"CENTER", "bold":true}, ...]
     */
    fun printList(jsonArrayString: String) {
        try {
            if (printer == null) {
                printer = Printer.getInstance(context, this)
            }

            val jsonArray = org.json.JSONArray(jsonArrayString)

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                
                val text = item.optString("text", "")
                val align = item.optString("align", "LEFT")
                val size = item.optInt("size", 22)
                val bold = item.optBoolean("bold", false)
                
                val textFormat = TextFormat()
                
                // Alinhamento
                when (align.uppercase()) {
                    "CENTER" -> textFormat.setAlignment(Alignment.CENTER)
                    "RIGHT" -> textFormat.setAlignment(Alignment.RIGHT)
                    else -> textFormat.setAlignment(Alignment.LEFT)
                }
                
                // Tamanho e Estilo
                textFormat.setFontSize(size)
                textFormat.setBold(bold)
                textFormat.setLineSpacing(0) // Zero extra spacing for compact print

                printer?.printText(textFormat, text)
            }

            // Apenas um pequeno avanço no final para cortar corretamente
            // printer?.scrollPaper(10) // DESATIVADO: Usuário relatou muito papel. Testando sem avanço.
            
            Log.d(TAG, "Impressão rica (JSON) finalizada")

        } catch (e: Exception) {
             Log.e(TAG, "Erro ao processar printList: ${e.message}")
             e.printStackTrace()
        }
    }

    // Callbacks obrigatórios da interface Printer.Listener

    override fun onPrinterError(error: PrinterError?) {
        val cause = error?.cause ?: "Desconhecida"
        Log.e(TAG, "Erro na impressora notificado pelo Listener: $cause")
    }

    override fun onPrinterSuccessful(id: Int) {
        Log.d(TAG, "Impressão concluída com sucesso. ID: $id")
    }
}
