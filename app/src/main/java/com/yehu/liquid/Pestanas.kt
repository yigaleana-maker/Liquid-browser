package com.yehu.liquid

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/* Una pestaña: su página, su vista previa para la cuadrícula y su WebView (se crea al usarla) */
class Pestana(val privada: Boolean, url: String = "", titulo: String = "") {
    val id = siguienteId++
    var url by mutableStateOf(url)
    var titulo by mutableStateOf(titulo)
    var icono by mutableStateOf<Bitmap?>(null)
    var vista by mutableStateOf<ImageBitmap?>(null)
    var progreso by mutableIntStateOf(100)
    var puedeAtras by mutableStateOf(false)
    var puedeAdelante by mutableStateOf(false)
    var escritorio by mutableStateOf(false)

    /* En la página de inicio (sin WebView) */
    val enInicio: Boolean get() = url.isEmpty()

    /* Si la pestaña empezó en Inicio, "atrás" desde la primera página vuelve ahí */
    var volverAInicio = false
    var web: WebView? = null

    companion object {
        private var siguienteId = 1L
    }
}

/* Lo que la pantalla le pide a la actividad: elegir archivos, video en pantalla completa, apps externas */
interface Anfitrion {
    fun elegirArchivos(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    fun descargar(url: String, agente: String?, disposicion: String?, tipo: String?, privada: Boolean)
    fun abrirFuera(intent: Intent): Boolean
}

private const val PERFIL_PRIVADO = "privado"

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

    /* Video en pantalla completa (YouTube y otros) */
    var pantallaCompleta by mutableStateOf<View?>(null)
    private var cerrarPantallaCompleta: WebChromeClient.CustomViewCallback? = null

    private var actividad: Activity? = null
    private var anfitrion: Anfitrion? = null

    fun normales() = pestanas.filter { !it.privada }
    fun privadas() = pestanas.filter { it.privada }

    fun iniciar(act: Activity, a: Anfitrion) {
        actividad = act
        anfitrion = a
        /* La actividad se volvió a crear: la pestaña actual recupera su WebView */
        if (pestanas.isNotEmpty()) {
            actual?.let { seleccionar(it) }
            return
        }
        borrarPerfilPrivado()
        val (guardadas, i) = PestanasGuardadas.leer(act)
        guardadas.forEach { pestanas.add(Pestana(false, it.url, it.titulo)) }
        if (pestanas.isEmpty()) pestanas.add(Pestana(false))
        seleccionar(pestanas[i.coerceIn(0, pestanas.size - 1)])
    }

    /* Al cerrarse la actividad se liberan los WebView; las pestañas se quedan con su dirección */
    fun liberar() {
        guardar()
        pestanas.forEach { p ->
            p.web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
            p.web = null
        }
        actividad = null
        anfitrion = null
    }

    fun guardar() {
        val ctx = actividad ?: return
        val l = normales()
        PestanasGuardadas.guardar(ctx, l.map { Sitio(it.url, it.titulo) }, l.indexOf(actual).coerceAtLeast(0))
    }

    fun seleccionar(p: Pestana) {
        actual?.let { if (it != p) { capturar(it); it.web?.onPause() } }
        actual = p
        buscandoEnPagina = false
        if (!p.enInicio && p.web == null) {
            crearWeb(p)?.loadUrl(p.url)
        }
        p.web?.onResume()
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
        val w = p.web ?: crearWeb(p)
        w?.loadUrl(url)
        guardar()
    }

    fun cerrar(p: Pestana) {
        val lista = if (p.privada) privadas() else normales()
        val i = lista.indexOf(p)
        p.web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        p.web = null
        pestanas.remove(p)
        if (actual == p) {
            val resto = lista - p
            when {
                resto.isNotEmpty() -> seleccionar(resto[(i - 1).coerceAtLeast(0).coerceAtMost(resto.size - 1)])
                p.privada -> normales().lastOrNull()?.let { seleccionar(it) } ?: nueva(false)
                else -> nueva(false)
            }
        }
        if (p.privada && privadas().isEmpty()) borrarPerfilPrivado()
        guardar()
    }

    fun cerrarVarias(l: Collection<Pestana>) {
        l.toList().forEach { cerrar(it) }
    }

    /* Atrás: la página anterior; si no hay y la pestaña empezó en Inicio, vuelve a Inicio */
    fun atras(): Boolean {
        val p = actual ?: return false
        val w = p.web
        if (w != null && w.canGoBack()) {
            w.goBack()
            return true
        }
        if (!p.enInicio && p.volverAInicio) {
            irAInicio(p)
            return true
        }
        return false
    }

    fun irAInicio(p: Pestana) {
        p.web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        p.web = null
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
        actual?.web?.takeIf { it.canGoForward() }?.goForward()
    }

    fun recargar() {
        val w = actual?.web ?: return
        if ((actual?.progreso ?: 100) < 100) w.stopLoading() else w.reload()
    }

    fun alternarEscritorio(p: Pestana) {
        p.escritorio = !p.escritorio
        val w = p.web ?: return
        aplicarAgente(w, p.escritorio)
        w.reload()
    }

    /* Vista previa de la pestaña para la cuadrícula: lo que se ve en pantalla, a la mitad de tamaño */
    fun capturar(p: Pestana) {
        val w = p.web ?: return
        val padre = w.parent as? View ?: return
        if (w.width <= 0 || w.height <= 0) return
        runCatching {
            val esc = 0.5f
            val bmp = Bitmap.createBitmap((w.width * esc).toInt(), (w.height * esc).toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(esc, esc)
            c.translate(-w.left.toFloat(), -w.top.toFloat())
            padre.draw(c)
            p.vista = bmp.asImageBitmap()
        }
    }

    fun buscarEnPagina(texto: String) {
        val w = actual?.web ?: return
        if (texto.isEmpty()) {
            w.clearMatches()
            resultadosBusqueda = 0 to 0
        } else {
            w.findAllAsync(texto)
        }
    }

    fun siguienteResultado(adelante: Boolean) {
        actual?.web?.findNext(adelante)
    }

    fun terminarBusqueda() {
        actual?.web?.clearMatches()
        buscandoEnPagina = false
        resultadosBusqueda = 0 to 0
    }

    fun salirDePantallaCompleta() {
        cerrarPantallaCompleta?.onCustomViewHidden()
        cerrarPantallaCompleta = null
        pantallaCompleta = null
    }

    private fun borrarPerfilPrivado() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        runCatching {
            val ps = ProfileStore.getInstance()
            if (ps.allProfileNames.contains(PERFIL_PRIVADO)) ps.deleteProfile(PERFIL_PRIVADO)
        }
    }

    private fun aplicarAgente(w: WebView, escritorio: Boolean) {
        val normal = WebSettings.getDefaultUserAgent(w.context)
        w.settings.userAgentString = if (escritorio) {
            normal.replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")
        } else {
            null
        }
        w.settings.useWideViewPort = true
        w.settings.loadWithOverviewMode = escritorio
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun crearWeb(p: Pestana): WebView? {
        val act = actividad ?: return null
        val w = WebView(act)
        /* Las pestañas privadas usan su propio perfil: cookies y datos aparte, que se borran al cerrarlas */
        if (p.privada && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { WebViewCompat.setProfile(w, PERFIL_PRIVADO) }
        }
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            if (p.privada) cacheMode = WebSettings.LOAD_NO_CACHE
        }
        if (p.escritorio) aplicarAgente(w, true)
        runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(w, !p.privada) }

        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val esquema = u.scheme?.lowercase() ?: return false
                if (esquema == "http" || esquema == "https" || esquema == "about" || esquema == "data" ||
                    esquema == "blob" || esquema == "javascript"
                ) return false
                abrirFuera(view, u.toString())
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (p.web != view) return
                p.url = url
                p.icono = favicon ?: Iconos.de(view.context, url)
                p.progreso = 5
                p.puedeAtras = view.canGoBack()
                p.puedeAdelante = view.canGoForward()
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (p.web != view) return
                p.progreso = 100
                p.puedeAtras = view.canGoBack()
                p.puedeAdelante = view.canGoForward()
                if (p == actual) guardar()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                if (p.web != view) return
                p.url = url
                p.puedeAtras = view.canGoBack()
                p.puedeAdelante = view.canGoForward()
                if (!p.privada && !isReload) Historial.agregar(view.context, url, view.title.orEmpty())
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (p.web == view) p.progreso = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (p.web != view) return
                val t = title.orEmpty()
                p.titulo = if (t.startsWith("http") || t == view.url) "" else t
                if (!p.privada) Historial.actualizarTitulo(view.context, view.url.orEmpty(), p.titulo)
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                if (p.web != view || icon == null) return
                p.icono = icon
                if (!p.privada) Iconos.guardar(view.context, view.url.orEmpty(), icon)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                cerrarPantallaCompleta?.onCustomViewHidden()
                cerrarPantallaCompleta = callback
                pantallaCompleta = view
            }

            override fun onHideCustomView() {
                cerrarPantallaCompleta = null
                pantallaCompleta = null
            }

            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean = anfitrion?.elegirArchivos(filePathCallback, fileChooserParams) ?: false

            /* Enlaces que piden ventana nueva (target="_blank"): se abren en una pestaña nueva */
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture) return false
                val nueva = Pestana(p.privada)
                val i = pestanas.indexOf(p) + 1
                pestanas.add(i.coerceIn(0, pestanas.size), nueva)
                val wn = crearWeb(nueva) ?: return false
                nueva.url = "about:blank"
                seleccionar(nueva)
                val transporte = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transporte.webView = wn
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                pestanas.firstOrNull { it.web == window }?.let { cerrar(it) }
            }
        }

        w.setDownloadListener { url, agente, disposicion, tipo, _ ->
            anfitrion?.descargar(url, agente, disposicion, tipo, p.privada)
        }
        w.setFindListener { activo, total, terminado ->
            if (terminado && p == actual) resultadosBusqueda = (if (total == 0) 0 else activo + 1) to total
        }
        p.web = w
        return w
    }

    /* intent://, mailto:, tel:, market: … se abren con la app que corresponda */
    private fun abrirFuera(view: WebView, url: String) {
        val a = anfitrion ?: return
        val intent = runCatching {
            if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }.getOrNull() ?: return
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        if (!a.abrirFuera(intent)) {
            intent.getStringExtra("browser_fallback_url")?.let { view.loadUrl(it) }
        }
    }
}

/* Pantallas de lista que se abren desde el menú */
enum class Lista(val titulo: String) {
    FAVORITOS("Favoritos"),
    HISTORIAL("Historial"),
    DESCARGAS("Descargas")
}
