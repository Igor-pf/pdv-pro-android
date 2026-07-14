package com.example.kioskpdv

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * Interface de comunicação entre o Frontend (JS) e a Maquininha (Stone/Outras)
 */
class AndroidPayment(private val context: Context) {

    /**
     * Inicia uma transação de pagamento nativo
     * @param amount Valor em centavos (ex: 1000 para R$ 10,00)
     * @param type "CREDITO", "DEBITO", "PIX" ou "VOUCHER"
     * @param installment Número de parcelas
     * @param orderId ID único do pedido/mesa
     */
    @JavascriptInterface
    fun startTransaction(amount: Int, type: String, installment: Int, orderId: String) {
        try {
            // Integração genérica Stone SmartPOS via Intent (DeepLink Scheme)
            // A documentação da Stone fornece schemas específicos para iniciar o app deles
            val intent = Intent("br.com.stone.pos.action.PAYMENT")
            intent.putExtra("amount", amount.toString()) // Algumas versões pedem string
            intent.putExtra("transaction_type", type)
            intent.putExtra("installment", installment)
            intent.putExtra("order_id", orderId)
            
            // O retorno virá para o nosso app através deste scheme configurado no Manifest
            intent.putExtra("return_scheme", "kioskpdv://payment_result")

            // Adiciona flag para abrir fora de context se necessário
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Aplicativo de pagamento da Stone não encontrado ou não homologado.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao tentar integrar pagamento: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
