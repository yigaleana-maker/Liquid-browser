package com.yehu.liquid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS = "liquid"

/* El buscador por defecto: DuckDuckGo */
const val BUSCADOR = "https://duckduckgo.com/?q="

/* Lo que escribes en la barra: si parece una dirección se abre, si no, se busca en DuckDuckGo */
fun aDireccion(texto: String): String {
    val t = texto.trim()
    if (t.isEmpty()) return ""
    val conEsquema = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
    if (conEsquema.matches(t) || t.startsWith("about:") || t.startsWith("data:")) return t
    val pareceDominio = !t.contains(' ') && (
        Regex("^[^/\\s]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$").matches(t) ||
            Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$").matches(t)
        )
    return if (pareceDominio) "https://$t" else BUSCADOR + Uri.encode(t)
}

/* Si la dirección es una búsqueda de DuckDuckGo, lo que buscaste (para mostrarlo en la barra) */
fun busquedaDe(url: String): String? {
    val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val host = u.host ?: return null
    if (!host.endsWith("duckduckgo.com")) return null
    return u.getQueryParameter("q")?.takeIf { it.isNotBlank() }
}

/* "www.ejemplo.com" → "ejemplo.com" */
fun hostDe(url: String): String =
    runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.").orEmpty()

data class Sitio(val url: String, val titulo: String, val cuando: Long = System.currentTimeMillis())

private fun leerLista(ctx: Context, clave: String): List<Sitio> = try {
    val arr = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(clave, "[]") ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Sitio(o.optString("url"), o.optString("titulo"), o.optLong("cuando"))
    }
} catch (e: Exception) {
    emptyList()
}

private fun guardarLista(ctx: Context, clave: String, lista: List<Sitio>) {
    val arr = JSONArray()
    lista.forEach { arr.put(JSONObject().put("url", it.url).put("titulo", it.titulo).put("cuando", it.cuando)) }
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(clave, arr.toString()).apply()
}

/* Favoritos: aparecen en la página de inicio */
object Favoritos {
    var version by mutableIntStateOf(0)
        private set

    fun todos(ctx: Context): List<Sitio> = leerLista(ctx, "favoritos")

    fun es(ctx: Context, url: String): Boolean = todos(ctx).any { mismo(it.url, url) }

    fun alternar(ctx: Context, url: String, titulo: String): Boolean {
        val l = todos(ctx)
        val ya = l.any { mismo(it.url, url) }
        guardarLista(ctx, "favoritos", if (ya) l.filterNot { mismo(it.url, url) } else l + Sitio(url, titulo.ifBlank { hostDe(url) }))
        version++
        return !ya
    }

    fun quitar(ctx: Context, url: String) {
        guardarLista(ctx, "favoritos", todos(ctx).filterNot { it.url == url })
        version++
    }

    fun mover(ctx: Context, desde: Int, hasta: Int) {
        val l = todos(ctx).toMutableList()
        if (desde !in l.indices || hasta !in l.indices) return
        l.add(hasta, l.removeAt(desde))
        guardarLista(ctx, "favoritos", l)
        version++
    }

    private fun mismo(a: String, b: String) = a.trimEnd('/') == b.trimEnd('/')
}

/* Historial: las páginas visitadas en pestañas normales (las privadas no se guardan) */
object Historial {
    var version by mutableIntStateOf(0)
        private set

    fun todos(ctx: Context): List<Sitio> = leerLista(ctx, "historial")

    fun agregar(ctx: Context, url: String, titulo: String) {
        if (!url.startsWith("http")) return
        val l = todos(ctx)
        /* Si la misma página se acaba de visitar, solo se actualiza su título y hora */
        val sinRepetir = if (l.firstOrNull()?.url == url) l.drop(1) else l
        guardarLista(ctx, "historial", (listOf(Sitio(url, titulo)) + sinRepetir).take(1500))
        version++
    }

    fun actualizarTitulo(ctx: Context, url: String, titulo: String) {
        val l = todos(ctx)
        if (l.firstOrNull()?.url != url || titulo.isBlank() || l.first().titulo == titulo) return
        guardarLista(ctx, "historial", listOf(l.first().copy(titulo = titulo)) + l.drop(1))
        version++
    }

    fun quitar(ctx: Context, s: Sitio) {
        guardarLista(ctx, "historial", todos(ctx).filterNot { it.url == s.url && it.cuando == s.cuando })
        version++
    }

    fun borrarTodo(ctx: Context) {
        guardarLista(ctx, "historial", emptyList())
        version++
    }

    /* Los sitios que más visitas, para la página de inicio */
    fun frecuentes(ctx: Context, n: Int): List<Sitio> =
        todos(ctx).groupBy { hostDe(it.url) }
            .filterKeys { it.isNotEmpty() && it != "duckduckgo.com" }
            .entries.sortedByDescending { it.value.size }
            .take(n)
            .map { (host, visitas) -> Sitio("https://$host", visitas.first().titulo.ifBlank { host }) }
}

/* Descargas hechas desde el navegador (los archivos quedan en la carpeta Descargas) */
data class Descarga(val id: Long, val nombre: String, val url: String, val cuando: Long, val archivo: String = "", val tipo: String = "")

object Descargas {
    var version by mutableIntStateOf(0)
        private set

    fun todas(ctx: Context): List<Descarga> = try {
        val arr = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("descargas", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Descarga(o.optLong("id"), o.optString("nombre"), o.optString("url"), o.optLong("cuando"), o.optString("archivo"), o.optString("tipo"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun guardar(ctx: Context, l: List<Descarga>) {
        val arr = JSONArray()
        l.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("nombre", it.nombre).put("url", it.url).put("cuando", it.cuando)
                    .put("archivo", it.archivo).put("tipo", it.tipo)
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("descargas", arr.toString()).apply()
        version++
    }

    fun agregar(ctx: Context, d: Descarga) = guardar(ctx, (listOf(d) + todas(ctx)).take(300))
    fun quitar(ctx: Context, d: Descarga) = guardar(ctx, todas(ctx).filterNot { it.id == d.id })
    fun borrarLista(ctx: Context) = guardar(ctx, emptyList())
}

/* Íconos de los sitios, guardados por dominio para la página de inicio y las listas */
object Iconos {
    var version by mutableIntStateOf(0)
        private set
    private val memoria = HashMap<String, Bitmap?>()

    private fun archivo(ctx: Context, host: String) =
        File(File(ctx.filesDir, "iconos").apply { mkdirs() }, host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png")

    fun de(ctx: Context, url: String): Bitmap? {
        val host = hostDe(url).ifEmpty { return null }
        return memoria.getOrPut(host) {
            val f = archivo(ctx, host)
            if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() else null
        }
    }

    private val pedidos = HashSet<String>()
    private val hilos = java.util.concurrent.Executors.newFixedThreadPool(2)
    private val principal = android.os.Handler(android.os.Looper.getMainLooper())

    /* Pide el ícono del sitio al servicio de íconos de DuckDuckGo */
    fun cargar(ctx: Context, url: String, alTerminar: (Bitmap) -> Unit) {
        val host = hostDe(url).ifEmpty { return }
        if (!pedidos.add(host)) return
        hilos.execute {
            val b = runCatching {
                val c = java.net.URL("https://icons.duckduckgo.com/ip3/$host.ico").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 4000
                c.readTimeout = 4000
                val bytes = c.inputStream.use { it.readBytes() }
                c.disconnect()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
            if (b != null && b.width >= 8) principal.post {
                guardar(ctx, url, b)
                alTerminar(b)
            }
        }
    }

    fun guardar(ctx: Context, url: String, icono: Bitmap) {
        val host = hostDe(url).ifEmpty { return }
        val actual = memoria[host]
        /* Se queda con el ícono más grande que haya visto */
        if (actual != null && actual.width >= icono.width) return
        memoria[host] = icono
        runCatching { FileOutputStream(archivo(ctx, host)).use { icono.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        version++
    }
}

/* Pestañas abiertas (las normales), para recuperarlas al volver a abrir la app */
object PestanasGuardadas {
    fun leer(ctx: Context): Pair<List<Sitio>, Int> = try {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(p.getString("pestanas", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Sitio(o.optString("url"), o.optString("titulo"))
        } to p.getInt("pestana_actual", 0)
    } catch (e: Exception) {
        emptyList<Sitio>() to 0
    }

    fun guardar(ctx: Context, l: List<Sitio>, actual: Int) {
        val arr = JSONArray()
        l.forEach { arr.put(JSONObject().put("url", it.url).put("titulo", it.titulo)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("pestanas", arr.toString())
            .putInt("pestana_actual", actual)
            .apply()
    }
}
