package com.yehu.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray

private sealed class Sugerencia {
    data class Buscar(val texto: String) : Sugerencia()
    data class Pagina(val sitio: Sitio, val favorito: Boolean) : Sugerencia()
}

/* Escribir una dirección o una búsqueda. El campo queda abajo, encima del teclado,
   y las sugerencias aparecen justo arriba, al alcance del pulgar. */
@Composable
fun EditorDireccion(p: Pestana) {
    val ctx = LocalContext.current
    val inicial = if (p.enInicio) "" else busquedaDe(p.url) ?: p.url
    var campo by remember { mutableStateOf(TextFieldValue(inicial, TextRange(0, inicial.length))) }
    val foco = remember { FocusRequester() }
    var deDuck by remember { mutableStateOf<List<String>>(emptyList()) }
    val esquema = MaterialTheme.colorScheme
    val texto = campo.text.trim()

    LaunchedEffect(Unit) { foco.requestFocus() }
    /* Sugerencias de DuckDuckGo mientras escribes */
    LaunchedEffect(texto) {
        if (texto.isEmpty() || texto == inicial) {
            deDuck = emptyList()
            return@LaunchedEffect
        }
        delay(180)
        deDuck = withContext(Dispatchers.IO) { sugerenciasDuck(texto) }
    }

    val locales = remember(texto) {
        if (texto.isEmpty()) {
            Favoritos.todos(ctx).take(8).map { Sugerencia.Pagina(it, true) }
        } else {
            val fav = Favoritos.todos(ctx).filter { coincide(it, texto) }.map { Sugerencia.Pagina(it, true) }
            val vistos = fav.map { it.sitio.url }.toMutableSet()
            val hist = if (p.privada) emptyList() else Historial.todos(ctx).asSequence()
                .filter { coincide(it, texto) && vistos.add(it.url) }
                .take(4).map { Sugerencia.Pagina(it, false) }.toList()
            (fav.take(3) + hist)
        }
    }
    val sugerencias: List<Sugerencia> = buildList {
        if (texto.isNotEmpty() && texto != inicial) add(Sugerencia.Buscar(texto))
        addAll(locales)
        deDuck.filter { !it.equals(texto, true) }.take(5).forEach { add(Sugerencia.Buscar(it)) }
    }

    fun ir(t: String) {
        Navegador.editando = false
        Navegador.abrir(p, t)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(esquema.background.copy(alpha = 0.97f))
            .clickable(interactionSource = null, indication = null) { Navegador.editando = false }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            /* Las sugerencias, de abajo hacia arriba */
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)
            ) {
                items(sugerencias) { s ->
                    when (s) {
                        is Sugerencia.Buscar -> FilaSugerencia(IC_BUSCAR, s.texto, null, { campo = TextFieldValue(s.texto + " ", TextRange(s.texto.length + 1)) }) { ir(s.texto) }
                        is Sugerencia.Pagina -> FilaSugerencia(
                            if (s.favorito) IC_ESTRELLA else IC_HISTORIAL,
                            s.sitio.titulo.ifBlank { hostDe(s.sitio.url) },
                            s.sitio.url.removePrefix("https://").removePrefix("www."),
                            null
                        ) { ir(s.sitio.url) }
                    }
                }
                if (texto.isEmpty() && locales.isNotEmpty()) {
                    item {
                        Text(
                            "Favoritos",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = esquema.onBackground.copy(alpha = 0.55f),
                            modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            /* El campo, en vidrio */
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .vidrio(RoundedCornerShape(26.dp), null)
                        .padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(IC_BUSCAR, null, Modifier.size(18.dp), tint = esquema.onSurface.copy(alpha = 0.55f))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (campo.text.isEmpty()) {
                            Text(
                                if (p.privada) "Buscar en privado" else "Buscar o escribir dirección",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = esquema.onSurface.copy(alpha = 0.5f), maxLines = 1
                            )
                        }
                        BasicTextField(
                            campo, { campo = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = Nunito, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = esquema.onSurface),
                            cursorBrush = SolidColor(esquema.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                            keyboardActions = KeyboardActions(onGo = { ir(campo.text) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(foco)
                        )
                    }
                    if (campo.text.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .combinedClickableSinEfecto { campo = TextFieldValue("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(IC_CERRAR, "Borrar", Modifier.size(18.dp), tint = esquema.onSurface.copy(alpha = 0.55f))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Cancelar",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = esquema.primary,
                    modifier = Modifier
                        .tocable { Navegador.editando = false }
                        .padding(horizontal = 6.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun FilaSugerencia(icono: ImageVector, titulo: String, detalle: String?, onCompletar: (() -> Unit)?, onClick: () -> Unit) {
    val esquema = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .combinedClickableSinEfecto(onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).vidrio(RoundedCornerShape(12.dp), null, sombra = 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icono, null, Modifier.size(18.dp), tint = esquema.onSurface.copy(alpha = 0.7f))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = esquema.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detalle != null) {
                Text(detalle, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = esquema.onBackground.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onCompletar != null) {
            /* Flecha: pasa la sugerencia al campo para seguir escribiendo */
            Box(Modifier.size(36.dp).combinedClickableSinEfecto(onCompletar), contentAlignment = Alignment.Center) {
                Icon(IC_FLECHA_DIAGONAL, "Completar", Modifier.size(18.dp), tint = esquema.onBackground.copy(alpha = 0.45f))
            }
        }
    }
}

private fun coincide(s: Sitio, t: String): Boolean =
    s.titulo.contains(t, true) || s.url.removePrefix("https://").removePrefix("http://").removePrefix("www.").startsWith(t, true) ||
        hostDe(s.url).contains(t, true)

/* Autocompletado de DuckDuckGo: [{"phrase": "..."}, ...] */
private fun sugerenciasDuck(t: String): List<String> = try {
    val c = URL("https://duckduckgo.com/ac/?q=" + URLEncoder.encode(t, "UTF-8")).openConnection() as HttpURLConnection
    c.connectTimeout = 3000
    c.readTimeout = 3000
    val cuerpo = c.inputStream.bufferedReader().use { it.readText() }
    c.disconnect()
    val arr = JSONArray(cuerpo)
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("phrase")?.takeIf { s -> s.isNotBlank() } }
} catch (e: Exception) {
    emptyList()
}
