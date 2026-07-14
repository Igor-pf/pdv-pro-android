package com.example.kioskpdv

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

class AndroidPrinter(private val context: Context, private val printer: GertecPrinter) {

    @JavascriptInterface
    fun imprimir(texto: String) {
        // Método legado ou simples
        Toast.makeText(context, "Imprimindo Texto...", Toast.LENGTH_SHORT).show()
        printer.printText(texto)
    }

    @JavascriptInterface
    fun printList(json: String) {
        // Novo método rico (JSON)
        // Toast.makeText(context, "Imprimindo Formatado...", Toast.LENGTH_SHORT).show()
        printer.printList(json)
    }
}
