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
            // Fonte monoespaçada não é explicitamente configurável no EasyLayer TextFormat simples, 
            // mas o padrão costuma ser adequado.

            printer?.printText(textFormat, text)
            printer?.scrollPaper(150) // Avança papel

            Log.d(TAG, "Comando de impressão enviado via EasyLayer")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao imprimir: ${e.message}")
            e.printStackTrace()
            // Tenta reconectar ou reiniciar serviço se possível, 
            // mas o EasyLayer gerencia muito disso sozinho.
        }
    }

    // Callbacks obrigatórios da interface Printer.Listener

    override fun onPrinterError(error: PrinterError?) {
        val cause = error?.cause ?: "Desconhecida"
        Log.e(TAG, "Erro na impressora notificado pelo Listener: $cause")
        // Opcional: Feedback visual ao usuário via Toast (requer MainThread)
    }

    override fun onPrinterSuccessful(id: Int) {
        Log.d(TAG, "Impressão concluída com sucesso. ID: $id")
    }
}
