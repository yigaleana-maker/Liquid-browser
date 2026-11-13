package com.yehu.liquid

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* Favoritos, Historial o Descargas, a pantalla completa */
@Composable
fun PantallaLista(tipo: Lista) {
    val ctx = LocalContext.current
    val esquema = MaterialTheme.colorScheme
    val haze = remember { HazeState() }
    var filtro by remember { mutableStateOf("") }
    var confirmarBorrado by remember { mutableStateOf(false) }
    val vf = Favoritos.version
    val vh = Historial.version
    val vd = Descargas.version
    val vi = Iconos.version

    fun abrir(url: String) {
        Navegador.lista = null
        val p = Navegador.actual
        if (p != null && p.enInicio) Navegador.abrir(p, url) else Navegador.nueva(p?.privada ?: false, url)
    }

    Box(Modifier.fillMaxSize().background(esquema.background)) {
        val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = arriba + 140.dp, bottom = abajo + 30.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            /* Usar las versiones aquí hace que la lista se vuelva a armar cuando cambian */
            if (vf + vh + vd < 0) return@LazyColumn
            when (tipo) {
                Lista.FAVORITOS -> {
                    val l = Favoritos.todos(ctx).filter { coincideFiltro(it, filtro) }
                    if (l.isEmpty()) item { Vacio("Aún no tienes favoritos. Toca ⋯ y la estrella en cualquier sitio.") }
                    items(l, key = { "f" + it.url }) { s ->
                        FilaSitio(s, vi, detalle = s.url.removePrefix("https://"), onBorrar = { Favoritos.quitar(ctx, s.url) }) { abrir(s.url) }
                    }
                }
                Lista.HISTORIAL -> {
                    val l = Historial.todos(ctx).filter { coincideFiltro(it, filtro) }
                    if (l.isEmpty()) item { Vacio(if (filtro.isEmpty()) "El historial está vacío." else "Nada coincide con tu búsqueda.") }
                    var diaAnterior = ""
                    l.forEach { s ->
                        val dia = nombreDia(s.cuando)
                        if (dia != diaAnterior) {
                            diaAnterior = dia
                            item(key = "d$dia${s.cuando}") {
                                Text(
                                    dia, fontSize = 14.sp, fontWeight = FontWeight.Black,
                                    color = esquema.onBackground.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp)
                                )
                            }
                        }
                        item(key = "h${s.url}${s.cuando}") {
                            FilaSitio(s, vi, detalle = hora(s.cuando) + " · " + hostDe(s.url), onBorrar = { Historial.quitar(ctx, s) }) { abrir(s.url) }
                        }
                    }
                }
                Lista.DESCARGAS -> {
                    val l = Descargas.todas(ctx).filter { filtro.isBlank() || it.nombre.contains(filtro, true) }
                    if (l.isEmpty()) item { Vacio("Lo que descargues aparecerá aquí. Los archivos se guardan en la carpeta Descargas.") }
                    items(l, key = { "x" + it.id }) { d ->
                        FilaDescarga(d, onBorrar = { Descargas.quitar(ctx, d) }) { abrirDescarga(ctx, d) }
                    }
                }
            }
        }
        /* Encabezado de vidrio */
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tipo.titulo, fontSize = 32.sp, fontWeight = FontWeight.Black, color = esquema.onBackground, modifier = Modifier.weight(1f))
                if (tipo != Lista.FAVORITOS) {
                    CirculoVidrio(IC_BORRAR, "Borrar todo", haze) { confirmarBorrado = true }
                    Spacer(Modifier.width(10.dp))
                }
                CirculoVidrio(IC_CERRAR, "Cerrar", haze) { Navegador.lista = null }
            }
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .vidrio(RoundedCornerShape(24.dp), haze)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(IC_BUSCAR, null, Modifier.size(18.dp), tint = esquema.onSurface.copy(alpha = 0.55f))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (filtro.isEmpty()) Text("Buscar", fontSize = 15.sp, color = esquema.onSurface.copy(alpha = 0.5f))
                    BasicTextField(
                        filtro, { filtro = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Nunito, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = esquema.onSurface),
                        cursorBrush = SolidColor(esquema.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text(if (tipo == Lista.HISTORIAL) "¿Borrar todo el historial?" else "¿Vaciar la lista de descargas?") },
            text = {
                Text(
                    if (tipo == Lista.HISTORIAL) "También se borran las cookies y los datos guardados de los sitios."
                    else "Los archivos se quedan en la carpeta Descargas."
                )
            },
            confirmButton = {
                TextButton({
                    if (tipo == Lista.HISTORIAL) {
                        Historial.borrarTodo(ctx)
                        Motor.de(ctx).storageController.clearData(
                            org.mozilla.geckoview.StorageController.ClearFlags.COOKIES or
                                org.mozilla.geckoview.StorageController.ClearFlags.SITE_DATA or
                                org.mozilla.geckoview.StorageController.ClearFlags.ALL_CACHES
                        )
                    } else {
                        Descargas.borrarLista(ctx)
                    }
                    confirmarBorrado = false
                }) { Text("Borrar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ confirmarBorrado = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Vacio(t: String) {
    Text(
        t, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(24.dp)
    )
}

@Composable
private fun FilaSitio(s: Sitio, versionIconos: Int, detalle: String, onBorrar: () -> Unit, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val esquema = MaterialTheme.colorScheme
    val icono = remember(s.url, versionIconos) { Iconos.de(ctx, s.url) }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickableSinEfecto(onClick)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).vidrio(RoundedCornerShape(14.dp), null, sombra = 0.dp), contentAlignment = Alignment.Center) {
            IconoSitio(icono, s.url, s.titulo, 24.dp)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(s.titulo.ifBlank { hostDe(s.url) }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = esquema.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detalle, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = esquema.onBackground.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(44.dp).combinedClickableSinEfecto(onBorrar), contentAlignment = Alignment.Center) {
            Icon(IC_CERRAR, "Quitar", Modifier.size(18.dp), tint = esquema.onBackground.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun FilaDescarga(d: Descarga, onBorrar: () -> Unit, onClick: () -> Unit) {
    val esquema = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickableSinEfecto(onClick)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).vidrio(RoundedCornerShape(14.dp), null, sombra = 0.dp), contentAlignment = Alignment.Center) {
            Icon(IC_ARCHIVO, null, Modifier.size(22.dp), tint = esquema.primary)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(d.nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = esquema.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                nombreDia(d.cuando) + " · " + hostDe(d.url),
                fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                color = esquema.onBackground.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.size(44.dp).combinedClickableSinEfecto(onBorrar), contentAlignment = Alignment.Center) {
            Icon(IC_CERRAR, "Quitar de la lista", Modifier.size(18.dp), tint = esquema.onBackground.copy(alpha = 0.45f))
        }
    }
}

private fun abrirDescarga(ctx: Context, d: Descarga) {
    if (d.archivo.isEmpty()) {
        Toast.makeText(ctx, "El archivo todavía no está listo", Toast.LENGTH_SHORT).show()
        return
    }
    val i = Intent(Intent.ACTION_VIEW)
        .setDataAndType(android.net.Uri.parse(d.archivo), d.tipo.ifEmpty { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }
        .onFailure { Toast.makeText(ctx, "No hay una app para abrir este archivo", Toast.LENGTH_SHORT).show() }
}

private fun coincideFiltro(s: Sitio, f: String) = f.isBlank() || s.titulo.contains(f, true) || s.url.contains(f, true)

private fun nombreDia(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val hoy = Calendar.getInstance()
    fun mismo(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (mismo(c, hoy)) return "Hoy"
    hoy.add(Calendar.DAY_OF_YEAR, -1)
    if (mismo(c, hoy)) return "Ayer"
    return SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Date(t)).replaceFirstChar { it.uppercase() }
}

private fun hora(t: Long): String = SimpleDateFormat("HH:mm", Locale("es")).format(Date(t))
