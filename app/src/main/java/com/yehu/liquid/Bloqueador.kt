package com.yehu.liquid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/* Bloqueador de anuncios: uBlock Origin, la extensión de Firefox. Se instala sola la primera vez
   desde addons.mozilla.org y se actualiza con Firefox. */
object Bloqueador {
    enum class Estado { BUSCANDO, INSTALANDO, ACTIVO, APAGADO, ERROR }

    private const val ID = "uBlock0@raymondhill.net"
    private const val XPI = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
    private const val PREFS = "liquid"

    var estado by mutableStateOf(Estado.BUSCANDO)
        private set
    private var extension: WebExtension? = null
    private var controlador: WebExtensionController? = null
    private var ctx: Context? = null

    /* La página de ajustes de uBlock, para abrirla en una pestaña */
    val ajustes: String? get() = extension?.metaData?.optionsPageUrl

    fun iniciar(c: Context, runtime: GeckoRuntime) {
        ctx = c
        val wc = runtime.webExtensionController
        controlador = wc
        /* Aceptamos los permisos que pide uBlock (también en pestañas privadas) */
        wc.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> =
                GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, false))
        }
        wc.list().accept({ l ->
            val u = l?.firstOrNull { it.id == ID }
            if (u != null) {
                extension = u
                estado = if (u.metaData.enabled) Estado.ACTIVO else Estado.APAGADO
            } else if (!c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("ublock_quitado", false)) {
                instalar()
            } else {
                estado = Estado.APAGADO
            }
        }, { estado = Estado.ERROR })
    }

    fun instalar() {
        val wc = controlador ?: return
        estado = Estado.INSTALANDO
        wc.install(XPI).accept({ e ->
            extension = e
            estado = if (e != null) Estado.ACTIVO else Estado.ERROR
            e?.let { wc.setAllowedInPrivateBrowsing(it, true).accept({ x -> if (x != null) extension = x }, { }) }
            ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("ublock_quitado", false)?.apply()
        }, { estado = Estado.ERROR })
    }

    /* Prender o apagar el bloqueador (o instalarlo si no está) */
    fun alternar() {
        val wc = controlador ?: return
        val e = extension
        when {
            e == null -> instalar()
            estado == Estado.ACTIVO -> wc.disable(e, WebExtensionController.EnableSource.USER).accept({ x ->
                if (x != null) extension = x
                estado = Estado.APAGADO
            }, { })
            else -> wc.enable(e, WebExtensionController.EnableSource.USER).accept({ x ->
                if (x != null) extension = x
                estado = Estado.ACTIVO
            }, { })
        }
    }
}
