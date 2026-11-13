package com.yehu.liquid

import android.app.AlertDialog
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.concurrent.Executors
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.WebResponse

class MainActivity : ComponentActivity(), Anfitrion {

    private var esperandoArchivos: Pair<FilePrompt, GeckoResult<PromptResponse>>? = null
    private val elegir = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val (prompt, resultado) = esperandoArchivos ?: return@registerForActivityResult
        esperandoArchivos = null
        val datos = r.data
        val uris = buildList {
            datos?.clipData?.let { c -> for (i in 0 until c.itemCount) add(c.getItemAt(i).uri) }
            if (isEmpty()) datos?.data?.let { add(it) }
        }
        resultado.complete(
            when {
                r.resultCode != RESULT_OK || uris.isEmpty() -> prompt.dismiss()
                prompt.type == FilePrompt.Type.MULTIPLE -> prompt.confirm(this, uris.toTypedArray())
                else -> prompt.confirm(this, uris.first())
            }
        )
    }
    private val hilos = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Navegador.iniciar(this, this)
        abrirIntent(intent)

        setContent {
            val p = Navegador.actual
            /* Las pestañas privadas se ven en oscuro, para que siempre sepas dónde estás */
            val privado = (p?.privada == true && !Navegador.verPestanas) || (Navegador.verPestanas && Navegador.cuadriculaPrivada)
            val sistema = isSystemInDarkTheme()
            val oscuro = privado || when (Apariencia.tema) {
                Tema.AUTO -> sistema
                Tema.CLARO -> false
                Tema.OSCURO -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !oscuro
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
        }

        /* Video a pantalla completa: sin barras del sistema */
        if (Navegador.pantallaCompleta) {
            DisposableEffect(Unit) {
                val c = WindowCompat.getInsetsController(window, window.decorView)
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
                onDispose { c.show(WindowInsetsCompat.Type.systemBars()) }
            }
        }

        BackHandler { alAtras() }
    }

    /* Atrás: cierra lo que esté encima; si no hay nada, la página anterior */
    private fun alAtras() {
        when {
            Navegador.pantallaCompleta -> Navegador.salirDePantallaCompleta()
            Navegador.menuAbierto -> Navegador.menuAbierto = false
            Navegador.editando -> Navegador.editando = false
            Navegador.lista != null -> Navegador.lista = null
            Navegador.buscandoEnPagina -> Navegador.terminarBusqueda()
            Navegador.verPestanas -> Navegador.verPestanas = false
            Navegador.atras() -> Unit
            else -> moveTaskToBack(true)
        }
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

    override fun elegirArchivos(prompt: FilePrompt, resultado: GeckoResult<PromptResponse>) {
        esperandoArchivos?.let { (pr, r) -> r.complete(pr.dismiss()) }
        esperandoArchivos = prompt to resultado
        val tipos = prompt.mimeTypes?.filter { it.isNotBlank() }.orEmpty()
        val i = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (tipos.size == 1) tipos[0] else "*/*")
        if (tipos.size > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, tipos.toTypedArray())
        if (prompt.type == FilePrompt.Type.MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            elegir.launch(Intent.createChooser(i, null))
        } catch (e: ActivityNotFoundException) {
            esperandoArchivos = null
            resultado.complete(prompt.dismiss())
        }
    }

    /* Las descargas se guardan en la carpeta Descargas del teléfono */
    override fun descargar(respuesta: WebResponse, privada: Boolean) {
        val cuerpo = respuesta.body
        if (cuerpo == null) {
            Toast.makeText(this, "No se pudo descargar", Toast.LENGTH_SHORT).show()
            return
        }
        val encabezados = respuesta.headers.mapKeys { it.key.lowercase() }
        val tipoServidor = encabezados["content-type"]?.substringBefore(';')?.trim()
        val nombre = URLUtil.guessFileName(respuesta.uri, encabezados["content-disposition"], tipoServidor)
        val tipo = tipoServidor?.takeIf { it != "application/octet-stream" }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(nombre.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        Toast.makeText(this, "Descargando $nombre", Toast.LENGTH_SHORT).show()
        val ctx = applicationContext
        hilos.execute {
            val resultado = runCatching {
                val valores = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, nombre)
                    put(MediaStore.Downloads.MIME_TYPE, tipo)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val r = ctx.contentResolver
                val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores) ?: error("sin destino")
                try {
                    r.openOutputStream(uri)?.use { salida -> cuerpo.use { it.copyTo(salida, 64 * 1024) } } ?: error("sin salida")
                    r.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    r.delete(uri, null, null)
                    throw e
                }
                uri
            }
            principal.post {
                resultado.onSuccess { uri ->
                    val ahora = System.currentTimeMillis()
                    Descargas.agregar(ctx, Descarga(ahora, nombre, if (privada) "" else respuesta.uri, ahora, uri.toString(), tipo))
                    Toast.makeText(ctx, "Listo: $nombre está en Descargas", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(ctx, "No se pudo descargar $nombre", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /* Mantener presionado un enlace o una imagen */
    override fun menuContextual(p: Pestana, elemento: GeckoSession.ContentDelegate.ContextElement) {
        val enlace = elemento.linkUri
        val imagen = elemento.srcUri?.takeIf { elemento.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE }
        val opciones = ArrayList<Pair<String, () -> Unit>>()
        if (enlace != null) {
            opciones += "Abrir en pestaña nueva" to { abrirAlLado(enlace, p.privada) }
            if (!p.privada) opciones += "Abrir en pestaña privada" to { abrirAlLado(enlace, true) }
            opciones += "Copiar enlace" to { copiar(enlace) }
            opciones += "Compartir enlace" to { compartir(enlace) }
        }
        if (imagen != null) {
            opciones += "Abrir imagen en pestaña nueva" to { abrirAlLado(imagen, p.privada) }
            opciones += "Copiar dirección de la imagen" to { copiar(imagen) }
            opciones += "Compartir imagen" to { compartir(imagen) }
        }
        if (opciones.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(enlace ?: imagen)
            .setItems(opciones.map { it.first }.toTypedArray()) { _, i -> opciones[i].second() }
            .show()
    }

    /* Una pestaña nueva en segundo plano (si es del mismo tipo), al lado de la actual */
    private fun abrirAlLado(url: String, privada: Boolean) {
        val actual = Navegador.actual
        val nueva = Navegador.nueva(privada, url)
        nueva.volverAInicio = false
        if (actual != null && privada == actual.privada) {
            Navegador.seleccionar(actual)
            Toast.makeText(this, "Se abrió en una pestaña nueva", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copiar(t: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Enlace", t))
    }

    private fun compartir(t: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
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
        Navegador.guardar()
    }

    override fun onDestroy() {
        Navegador.soltar(this)
        super.onDestroy()
    }
}
