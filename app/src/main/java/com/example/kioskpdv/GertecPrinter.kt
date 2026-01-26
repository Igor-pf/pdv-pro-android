package com.example.kioskpdv

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import br.com.gertec.gedi.GEDI
import br.com.gertec.gedi.enums.GEDI_PRNTR_e_Alignment
import br.com.gertec.gedi.enums.GEDI_PRNTR_e_Status
import br.com.gertec.gedi.interfaces.IGEDI
import br.com.gertec.gedi.interfaces.IGEDI_PRNTR
import br.com.gertec.gedi.structs.GEDI_PRNTR_st_StringConfig

class GertecPrinter(private val context: Context) {

    private var iGedi: IGEDI? = null
    private var printer: IGEDI_PRNTR? = null
    private val TAG = "GertecPrinter"
    private var isInitialized = false

    init {
        // Inicialização em background para não travar a UI Thread
        Thread {
            initializeGedi()
        }.start()
    }

    private fun initializeGedi() {
        try {
            if (!isInitialized) {
                GEDI.init(context)
                iGedi = GEDI.getInstance(context)
                printer = iGedi?.getPRNTR()
                Log.d(TAG, "GEDI Inicializado com sucesso")
                isInitialized = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao inicializar GEDI: ${e.message}")
            e.printStackTrace()
            isInitialized = false
        }
    }

    // Método público para checar se está pronto
    fun isReady(): Boolean {
        return isInitialized && printer != null
    }

    fun printText(text: String) {
        Thread {
            try {
                // Tenta reinicializar se necessário
                if (!isInitialized || printer == null) {
                    Log.w(TAG, "Impressora não inicializada, tentando reconectar...")
                    initializeGedi()
                }

                if (printer == null) {
                    Log.e(TAG, "Falha: Objeto impressora é nulo.")
                    return@Thread
                }

                // Inicializa o módulo de impressão específico se necessário
                // Nota: Algumas versões do GEDI pedem Init a cada uso, outras não. 
                // Para performance, ideal é manter, mas vamos garantir o Init.
                printer?.Init()
                
                // Checagem rápida de status
                try {
                   val status = printer?.Status()
                   if (status != GEDI_PRNTR_e_Status.OK) {
                       Log.e(TAG, "Status da impressora com erro: $status")
                       // Dependendo do erro (ex: SEM PAPEL), poderíamos retornar ou notificar
                   }
                } catch (e: Exception) {
                    Log.w(TAG, "Erro ao checar status: ${e.message}")
                }

                val config = GEDI_PRNTR_st_StringConfig(
                    Paint(),
                    GEDI_PRNTR_e_Alignment.LEFT,
                    true // WordWrap
                )
                
                // Configuração Otimizada da Fonte
                config.paint.textSize = 22f // Tamanho legível
                config.paint.typeface = Typeface.MONOSPACE // Fonte monoespaçada alinha melhor colunas

                printer?.DrawStringExt(config, text)
                
                // Avanço de papel (Feed)
                printer?.DrawBlankLine(150)
                
                // Comando final de impressão
                printer?.Output()
                
                Log.d(TAG, "Impressão enviada com sucesso.")

            } catch (e: Exception) {
                Log.e(TAG, "Erro crítico na impressão: ${e.message}")
                e.printStackTrace()
                // Força reinicialização na próxima tentativa se deu erro grave
                isInitialized = false
            }
        }.start()
    }
}
