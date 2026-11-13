package com.yehu.liquid

import android.app.DownloadManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity(), Anfitrion {

    private var esperandoArchivos: ValueCallback<Array<Uri>>? = null
    private val elegir = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        esperandoArchivos?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        esperandoArchivos = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Navegador.iniciar(this, this)
        abrirIntent(intent)

        setContent {
            val p = Navegador.actual
            /* Las pestañas privadas se ven en oscuro, para que siempre sepas dónde estás */
            val privado = (p?.privada == true && !Navegador.verPestanas) || (Navegador.verPestanas && Navegador.cuadriculaPrivada)
            val oscuro = privado || isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !oscuro && Navegador.pantallaCompleta == null
                    isAppearanceLightNavigationBars = !oscuro
                }
            }
            TemaLiquid(oscuroForzado = oscuro) {
                App()
            }
        }
    }

    @Composable
    private fun App() {
        val p = Navegador.actual
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (p != null) PantallaNavegador(p)

            AnimatedVisibility(
                Navegador.verPestanas,
                enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 1.08f),
                exit = fadeOut(tween(200)) + scaleOut(tween(220), targetScale = 1.08f)
            ) {
                CuadriculaPestanas()
            }
            AnimatedVisibility(
                Navegador.lista != null,
                enter = slideInVertically(tween(280)) { it / 3 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(240)) { it / 3 } + fadeOut(tween(180))
            ) {
                Navegador.lista?.let { PantallaLista(it) }
            }
            AnimatedVisibility(Navegador.menuAbierto && p != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                p?.let { MenuNavegador(it) }
            }
            if (Navegador.editando && p != null) EditorDireccion(p)

            Navegador.pantallaCompleta?.let { v -> PantallaCompleta(v) }
        }

        BackHandler { alAtras() }
    }

    /* Atrás: cierra lo que esté encima; si no hay nada, la página anterior */
    private fun alAtras() {
        when {
                Navegador.pantallaCompleta != null -> Navegador.salirDePantallaCompleta()
                Navegador.menuAbierto -> Navegador.menuAbierto = false
                Navegador.editando -> Navegador.editando = false
                Navegador.lista != null -> Navegador.lista = null
                Navegador.buscandoEnPagina -> Navegador.terminarBusqueda()
                Navegador.verPestanas -> Navegador.verPestanas = false
                Navegador.atras() -> Unit
            else -> moveTaskToBack(true)
        }
    }

    /* El video a pantalla completa, sin barras del sistema */
    @Composable
    private fun PantallaCompleta(v: android.view.View) {
        DisposableEffect(v) {
            val c = WindowCompat.getInsetsController(window, window.decorView)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { c.show(WindowInsetsCompat.Type.systemBars()) }
        }
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    (v.parent as? ViewGroup)?.removeView(v)
                    addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirIntent(intent)
    }

    /* Enlaces de otras apps, búsquedas y texto compartido: se abren en una pestaña nueva */
    private fun abrirIntent(i: Intent?) {
        i ?: return
        val texto = when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_WEB_SEARCH -> i.getStringExtra(SearchManager.QUERY)
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)?.let { t ->
                /* Si el texto compartido trae un enlace, se abre el enlace */
                Regex("https?://\\S+").find(t)?.value ?: t
            }
            else -> null
        }
        if (texto.isNullOrBlank()) return
        i.action = Intent.ACTION_MAIN
        Navegador.verPestanas = false
        Navegador.lista = null
        Navegador.editando = false
        val p = Navegador.actual
        if (p != null && p.enInicio && !p.privada) Navegador.abrir(p, texto) else Navegador.nueva(false, texto)
    }

    override fun elegirArchivos(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        esperandoArchivos?.onReceiveValue(null)
        esperandoArchivos = callback
        return try {
            elegir.launch(params.createIntent())
            true
        } catch (e: ActivityNotFoundException) {
            esperandoArchivos = null
            false
        }
    }

    override fun descargar(url: String, agente: String?, disposicion: String?, tipo: String?, privada: Boolean) {
        if (!url.startsWith("http")) {
            Toast.makeText(this, "Este archivo no se puede descargar", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val nombre = URLUtil.guessFileName(url, disposicion, tipo)
            val r = DownloadManager.Request(Uri.parse(url))
                .setTitle(nombre)
                .setMimeType(tipo)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nombre)
            if (!privada) CookieManager.getInstance().getCookie(url)?.let { r.addRequestHeader("Cookie", it) }
            agente?.let { r.addRequestHeader("User-Agent", it) }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(r)
            Descargas.agregar(this, Descarga(id, nombre, url, System.currentTimeMillis()))
            Toast.makeText(this, "Descargando $nombre", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo descargar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun abrirFuera(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    override fun onPause() {
        super.onPause()
        Navegador.actual?.web?.onPause()
        Navegador.guardar()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        Navegador.actual?.web?.onResume()
    }

    override fun onDestroy() {
        Navegador.liberar()
        super.onDestroy()
    }
}
