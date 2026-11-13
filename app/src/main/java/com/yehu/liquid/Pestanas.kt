package com.yehu.liquid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

/* Una pestaña: su página, su vista previa para la cuadrícula y su sesión de Firefox (se crea al usarla) */
class Pestana(val privada: Boolean, url: String = "", titulo: String = "") {
    val id = siguienteId++
    var url by mutableStateOf(url)
    var titulo by mutableStateOf(titulo)
    var icono by mutableStateOf<Bitmap?>(null)
    var vista by mutableStateOf<ImageBitmap?>(null)
    var progreso by mutableIntStateOf(100)
    var puedeAtras by mutableStateOf(false)
    var puedeAdelante by mutableStateOf(false)
    var segura by mutableStateOf(false)
    var escritorio by mutableStateOf(false)

    /* En la página de inicio (sin sesión) */
    val enInicio: Boolean get() = url.isEmpty()

    /* Si la pestaña empezó en Inicio, "atrás" desde la primera página vuelve ahí */
    var volverAInicio = false
    var sesion: GeckoSession? = null

    companion object {
        private var siguienteId = 1L
    }
}

/* Lo que la pantalla le pide a la actividad */
interface Anfitrion {
    fun elegirArchivos(prompt: GeckoSession.PromptDelegate.FilePrompt, resultado: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>)
    fun descargar(respuesta: WebResponse, privada: Boolean)
    fun abrirFuera(intent: Intent): Boolean
    fun menuContextual(p: Pestana, elemento: GeckoSession.ContentDelegate.ContextElement)
}

/* El motor de Firefox (GeckoView): uno solo para toda la app */
object Motor {
    private var runtime: GeckoRuntime? = null

    fun de(ctx: Context): GeckoRuntime = runtime ?: run {
        /* Protección contra rastreo de Firefox: bloquea rastreadores, criptomineros y huellas digitales,
           y aísla las cookies de cada sitio (Protección total de cookies) */
        val bloqueo = ContentBlocking.Settings.Builder()
            .antiTracking(
                ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.CRYPTOMINING or
                    ContentBlocking.AntiTracking.FINGERPRINTING or ContentBlocking.AntiTracking.EMAIL
            )
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .queryParameterStrippingEnabled(true)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .build()
        val ajustes = GeckoRuntimeSettings.Builder()
            .contentBlocking(bloqueo)
            .consoleOutput(false)
            .build()
        GeckoRuntime.create(ctx.applicationContext, ajustes).also {
            runtime = it
            Apariencia.cargar(ctx)
            Apariencia.aplicar(it)
            Extensiones.iniciar(it)
        }
    }
}

object Navegador {
    val pestanas = mutableStateListOf<Pestana>()
    var actual by mutableStateOf<Pestana?>(null)

    /* Lo que se ve encima del navegador */
    var verPestanas by mutableStateOf(false)
    var cuadriculaPrivada by mutableStateOf(false)
    var editando by mutableStateOf(false)
    var menuAbierto by mutableStateOf(false)
    var lista by mutableStateOf<Lista?>(null)
    var buscandoEnPagina by mutableStateOf(false)
    var resultadosBusqueda by mutableStateOf(0 to 0)

    /* Video a pantalla completa (YouTube y otros) */
    var pantallaCompleta by mutableStateOf(false)

    /* El GeckoView que está en pantalla, para las vistas previas */
    var vistaActual: GeckoView? = null

    var actividad: Activity? = null
        private set
    private var anfitrion: Anfitrion? = null
    private val principal = Handler(Looper.getMainLooper())

    fun normales() = pestanas.filter { !it.privada }
    fun privadas() = pestanas.filter { it.privada }

    fun iniciar(act: Activity, a: Anfitrion) {
        actividad = act
        anfitrion = a
        Motor.de(act)
        if (pestanas.isNotEmpty()) return
        val (guardadas, i) = PestanasGuardadas.leer(act)
        guardadas.forEach { pestanas.add(Pestana(false, it.url, it.titulo)) }
        if (pestanas.isEmpty()) pestanas.add(Pestana(false))
        seleccionar(pestanas[i.coerceIn(0, pestanas.size - 1)])
    }

    fun soltar(act: Activity) {
        guardar()
        if (actividad == act) {
            actividad = null
            anfitrion = null
        }
    }

    fun guardar() {
        val ctx = actividad ?: return
        val l = normales()
        PestanasGuardadas.guardar(ctx, l.map { Sitio(it.url, it.titulo) }, l.indexOf(actual).coerceAtLeast(0))
    }

    fun seleccionar(p: Pestana) {
        val antes = actual
        if (antes != null && antes != p) {
            capturar(antes)
            antes.sesion?.setActive(false)
        }
        actual = p
        buscandoEnPagina = false
        if (!p.enInicio && p.sesion == null) {
            crearSesion(p)?.loadUri(p.url)
        }
        p.sesion?.setActive(true)
        guardar()
    }

    fun nueva(privada: Boolean, url: String = ""): Pestana {
        val p = Pestana(privada)
        val i = actual?.let { pestanas.indexOf(it) + 1 }?.takeIf { it > 0 && actual?.privada == privada } ?: pestanas.size
        pestanas.add(i, p)
        seleccionar(p)
        if (url.isNotEmpty()) abrir(p, url) else p.volverAInicio = true
        return p
    }

    /* Abre una dirección (o búsqueda) en la pestaña */
    fun abrir(p: Pestana, texto: String) {
        val url = aDireccion(texto)
        if (url.isEmpty()) return
        if (p.enInicio) p.volverAInicio = true
        p.url = url
        p.icono = Iconos.de(actividad ?: return, url)
        val s = p.sesion ?: crearSesion(p)
        s?.loadUri(url)
        guardar()
    }

    fun cerrar(p: Pestana) {
        val lista = if (p.privada) privadas() else normales()
        val i = lista.indexOf(p)
        cerrarSesion(p)
        pestanas.remove(p)
        if (actual == p) {
            val resto = lista - p
            when {
                resto.isNotEmpty() -> seleccionar(resto[(i - 1).coerceAtLeast(0).coerceAtMost(resto.size - 1)])
                p.privada -> normales().lastOrNull()?.let { seleccionar(it) } ?: nueva(false)
                else -> nueva(false)
            }
        }
        guardar()
    }

    fun cerrarVarias(l: Collection<Pestana>) {
        l.toList().forEach { cerrar(it) }
    }

    private fun cerrarSesion(p: Pestana) {
        val s = p.sesion ?: return
        if (vistaActual?.session == s) vistaActual?.releaseSession()
        runCatching { s.close() }
        p.sesion = null
    }

    /* Atrás: la página anterior; si no hay y la pestaña empezó en Inicio, vuelve a Inicio */
    fun atras(): Boolean {
        val p = actual ?: return false
        val s = p.sesion
        if (s != null && p.puedeAtras) {
            s.goBack()
            return true
        }
        if (!p.enInicio && p.volverAInicio) {
            irAInicio(p)
            return true
        }
        return false
    }

    fun irAInicio(p: Pestana) {
        cerrarSesion(p)
        p.url = ""
        p.titulo = ""
        p.icono = null
        p.vista = null
        p.puedeAtras = false
        p.puedeAdelante = false
        p.progreso = 100
        guardar()
    }

    fun adelante() {
        val p = actual ?: return
        if (p.puedeAdelante) p.sesion?.goForward()
    }

    fun recargar() {
        val p = actual ?: return
        val s = p.sesion ?: return
        if (p.progreso in 1..99) s.stop() else s.reload()
    }

    fun alternarEscritorio(p: Pestana) {
        p.escritorio = !p.escritorio
        val s = p.sesion ?: return
        s.settings.userAgentMode =
            if (p.escritorio) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        s.settings.viewportMode =
            if (p.escritorio) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        s.reload()
    }

    /* Vista previa de la pestaña para la cuadrícula: lo que se ve en pantalla, más chiquito */
    fun capturar(p: Pestana) {
        val v = vistaActual ?: return
        val s = p.sesion ?: return
        if (v.session != s || v.width <= 0 || v.height <= 0) return
        runCatching {
            v.capturePixels().accept({ bmp ->
                if (bmp != null && p.sesion == s) {
                    val chico = Bitmap.createScaledBitmap(bmp, bmp.width / 2, bmp.height / 2, true)
                    p.vista = chico.asImageBitmap()
                }
            }, { })
        }
    }

    fun buscarEnPagina(texto: String) {
        val s = actual?.sesion ?: return
        if (texto.isEmpty()) {
            s.finder.clear()
            resultadosBusqueda = 0 to 0
        } else {
            s.finder.displayFlags = GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL
            s.finder.find(texto, 0).accept({ r -> if (r != null) resultadosBusqueda = r.current to r.total }, { })
        }
    }

    fun siguienteResultado(adelante: Boolean) {
        val s = actual?.sesion ?: return
        s.finder.find(null, if (adelante) GeckoSession.FINDER_FIND_FORWARD else GeckoSession.FINDER_FIND_BACKWARDS)
            .accept({ r -> if (r != null) resultadosBusqueda = r.current to r.total }, { })
    }

    fun terminarBusqueda() {
        actual?.sesion?.finder?.clear()
        buscandoEnPagina = false
        resultadosBusqueda = 0 to 0
    }

    fun salirDePantallaCompleta() {
        actual?.sesion?.exitFullScreen()
        pantallaCompleta = false
    }

    private fun crearSesion(p: Pestana, abrir: Boolean = true): GeckoSession? {
        val act = actividad ?: return null
        val ajustes = GeckoSessionSettings.Builder()
            .usePrivateMode(p.privada)
            .useTrackingProtection(true)
            .userAgentMode(if (p.escritorio) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (p.escritorio) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .suspendMediaWhenInactive(false)
            .build()
        val s = GeckoSession(ajustes)
        configurar(s, p, act)
        if (abrir) s.open(Motor.de(act))
        p.sesion = s
        return s
    }

    private fun configurar(s: GeckoSession, p: Pestana, ctx: Context) {
        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (p.sesion != session) return
                p.progreso = 5
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (p.sesion != session) return
                p.progreso = 100
                if (p == actual) {
                    guardar()
                    principal.postDelayed({ if (p == actual) capturar(p) }, 800)
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                if (p.sesion == session) p.progreso = progress.coerceAtLeast(5)
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                if (p.sesion == session) p.segura = securityInfo.isSecure
            }
        }

        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (p.sesion != session || url == null || url == "about:blank") return
                val otroSitio = hostDe(url) != hostDe(p.url)
                p.url = url
                if (otroSitio || p.icono == null) {
                    p.icono = Iconos.de(ctx, url)
                    if (p.icono == null && !p.privada) Iconos.cargar(ctx, url) { b -> if (hostDe(p.url) == hostDe(url)) p.icono = b }
                }
                if (!p.privada) Historial.agregar(ctx, url, p.titulo.takeIf { !otroSitio }.orEmpty())
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                if (p.sesion == session) p.puedeAtras = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                if (p.sesion == session) p.puedeAdelante = canGoForward
            }

            /* intent://, mailto:, tel:, market: … se abren con la app que corresponda */
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val esquema = Uri.parse(request.uri).scheme?.lowercase() ?: return null
                if (esquema in ESQUEMAS_WEB) return null
                abrirFuera(session, request.uri)
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            /* Enlaces que piden ventana nueva (target="_blank", window.open): pestaña nueva al lado */
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val nueva = Pestana(p.privada)
                nueva.escritorio = p.escritorio
                nueva.url = uri.ifEmpty { "about:blank" }
                val i = pestanas.indexOf(p) + 1
                pestanas.add(i.coerceIn(0, pestanas.size), nueva)
                val sn = crearSesion(nueva, abrir = false) ?: return null
                principal.post { seleccionar(nueva) }
                return GeckoResult.fromValue(sn)
            }
        }

        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (p.sesion != session) return
                val t = title.orEmpty()
                p.titulo = if (t.startsWith("http") || t == p.url) "" else t
                if (!p.privada) Historial.actualizarTitulo(ctx, p.url, p.titulo)
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (p == actual) pantallaCompleta = fullScreen
            }

            override fun onCloseRequest(session: GeckoSession) {
                if (p.sesion == session) principal.post { cerrar(p) }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                anfitrion?.descargar(response, p.privada)
            }

            override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) {
                if (element.linkUri != null || element.srcUri != null) anfitrion?.menuContextual(p, element)
            }

            /* Si el proceso de la página se cae, la pestaña se vuelve a abrir en la misma dirección */
            override fun onCrash(session: GeckoSession) = reabrir(session)
            override fun onKill(session: GeckoSession) = reabrir(session)

            private fun reabrir(session: GeckoSession) {
                if (p.sesion != session) return
                principal.post {
                    cerrarSesion(p)
                    if (p == actual && !p.enInicio) crearSesion(p)?.loadUri(p.url)
                }
            }
        }

        s.promptDelegate = Avisos(this::actividadActual, { anfitrion })
    }

    private fun actividadActual(): Activity? = actividad

    private fun abrirFuera(session: GeckoSession, url: String) {
        val a = anfitrion ?: return
        val intent = runCatching {
            if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }.getOrNull() ?: return
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        if (!a.abrirFuera(intent)) {
            intent.getStringExtra("browser_fallback_url")?.let { session.loadUri(it) }
        }
    }

    private val ESQUEMAS_WEB = setOf("http", "https", "about", "data", "blob", "javascript", "moz-extension", "resource")
}

/* Pantallas de lista que se abren desde el menú */
enum class Lista(val titulo: String) {
    FAVORITOS("Favoritos"),
    HISTORIAL("Historial"),
    DESCARGAS("Descargas")
}
