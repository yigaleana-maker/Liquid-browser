package com.yehu.liquid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/* Una extensión de Firefox que la app instala desde addons.mozilla.org y se puede prender o apagar.
   Si porDefecto es true se instala sola la primera vez. */
class ExtensionFirefox(private val id: String, slug: String, private val porDefecto: Boolean) {
    enum class Estado { BUSCANDO, INSTALANDO, ACTIVO, APAGADO, ERROR }

    private val xpi = "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi"

    var estado by mutableStateOf(Estado.BUSCANDO)
        private set
    private var extension: WebExtension? = null
    private var controlador: WebExtensionController? = null

    /* La página de ajustes de la extensión, para abrirla en una pestaña */
    val ajustes: String? get() = extension?.metaData?.optionsPageUrl

    fun iniciar(wc: WebExtensionController) {
        controlador = wc
        wc.list().accept({ l ->
            val u = l?.firstOrNull { it.id == id }
            when {
                u != null -> {
                    extension = u
                    estado = if (u.metaData.enabled) Estado.ACTIVO else Estado.APAGADO
                }
                porDefecto -> instalar()
                else -> estado = Estado.APAGADO
            }
        }, { estado = Estado.ERROR })
    }

    fun instalar() {
        val wc = controlador ?: return
        estado = Estado.INSTALANDO
        wc.install(xpi).accept({ e ->
            extension = e
            estado = if (e != null) Estado.ACTIVO else Estado.ERROR
            e?.let { wc.setAllowedInPrivateBrowsing(it, true).accept({ x -> if (x != null) extension = x }, { }) }
        }, { estado = Estado.ERROR })
    }

    /* Prender o apagar (o instalar si todavía no está) */
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

object Extensiones {
    /* Bloqueador de anuncios */
    val bloqueador = ExtensionFirefox("uBlock0@raymondhill.net", "ublock-origin", porDefecto = true)

    /* Oscurecer cualquier página */
    val oscurecedor = ExtensionFirefox("addon@darkreader.org", "darkreader", porDefecto = false)

    fun iniciar(runtime: GeckoRuntime) {
        val wc = runtime.webExtensionController
        /* Aceptamos los permisos que piden (también en pestañas privadas) */
        wc.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> =
                GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, false))
        }
        bloqueador.iniciar(wc)
        oscurecedor.iniciar(wc)
    }
}

/* Tema de la app: automático (como el teléfono), claro u oscuro */
enum class Tema(val nombre: String) { AUTO("Automático"), CLARO("Claro"), OSCURO("Oscuro") }

object Apariencia {
    private const val PREFS = "liquid"
    var tema by mutableStateOf(Tema.AUTO)
        private set

    fun cargar(ctx: Context) {
        tema = Tema.entries.firstOrNull { it.name == ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tema", null) } ?: Tema.AUTO
    }

    fun siguiente(ctx: Context, runtime: GeckoRuntime) {
        tema = Tema.entries[(tema.ordinal + 1) % Tema.entries.size]
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tema", tema.name).apply()
        aplicar(runtime)
    }

    /* Las páginas que tienen versión oscura la usan según el tema elegido */
    fun aplicar(runtime: GeckoRuntime) {
        runtime.settings.preferredColorScheme = when (tema) {
            Tema.AUTO -> org.mozilla.geckoview.GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
            Tema.CLARO -> org.mozilla.geckoview.GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            Tema.OSCURO -> org.mozilla.geckoview.GeckoRuntimeSettings.COLOR_SCHEME_DARK
        }
    }
}
