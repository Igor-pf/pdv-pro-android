package com.example.kioskpdv

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

class AndroidPrinter(private val context: Context, private val printer: GertecPrinter) {

    @JavascriptInterface
    fun imprimir(texto: String) {
        Toast.makeText(context, "Enviando para Impressora...", Toast.LENGTH_SHORT).show()
        printer.printText(texto)
    }
}
