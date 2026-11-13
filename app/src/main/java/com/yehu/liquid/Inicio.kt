package com.yehu.liquid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Inicio: gotas de color de fondo, tus favoritos y los sitios que más visitas */
@Composable
fun PaginaInicio(p: Pestana, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val vf = Favoritos.version
    val vh = Historial.version
    val vi = Iconos.version
    val favoritos = remember(vf) { Favoritos.todos(ctx) }
    val frecuentes = remember(vh, vf) {
        val fav = favoritos.map { hostDe(it.url) }.toSet()
        Historial.frecuentes(ctx, 12).filter { hostDe(it.url) !in fav }.take(8)
    }
    var borrar by remember { mutableStateOf<Sitio?>(null) }
    val esquema = MaterialTheme.colorScheme

    Box(modifier.background(esquema.background)) {
        FondoGotas(p.privada)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(56.dp))
            Text(
                if (p.privada) "Navegación privada" else "Liquid",
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = esquema.onBackground,
                lineHeight = 46.sp
            )
            if (p.privada) {
                Text(
                    "Lo que visites aquí no se guarda en el historial, y al cerrar las pestañas privadas " +
                        "se borran sus cookies y datos de los sitios.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = esquema.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    "Busca con DuckDuckGo o escribe una dirección abajo.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = esquema.onBackground.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (!p.privada || favoritos.isNotEmpty()) {
                Titulo("Favoritos")
                if (favoritos.isEmpty()) {
                    Text(
                        "Abre un sitio y toca ⋯ y luego la estrella para tenerlo aquí.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = esquema.onBackground.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .vidrio(RoundedCornerShape(22.dp), null, sombra = 0.dp)
                            .padding(18.dp)
                    )
                } else {
                    Cuadro(favoritos, vi, onLargo = { borrar = it }) { Navegador.abrir(p, it.url) }
                }
            }
            if (!p.privada && frecuentes.isNotEmpty()) {
                Titulo("Visitas frecuentes")
                Cuadro(frecuentes, vi, onLargo = null) { Navegador.abrir(p, it.url) }
            }
            Spacer(Modifier.height(ALTO_BARRA + 40.dp))
        }
    }

    borrar?.let { s ->
        AlertDialog(
            onDismissRequest = { borrar = null },
            title = { Text(s.titulo, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text("¿Quitar de favoritos?") },
            confirmButton = {
                TextButton({
                    Favoritos.quitar(ctx, s.url)
                    borrar = null
                }) { Text("Quitar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ borrar = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Titulo(t: String) {
    Text(
        t,
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 30.dp, bottom = 12.dp)
    )
}

/* Cuadro de sitios, cuatro por fila */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Cuadro(sitios: List<Sitio>, versionIconos: Int, onLargo: ((Sitio) -> Unit)?, onClick: (Sitio) -> Unit) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        sitios.chunked(4).forEach { fila ->
            Row(Modifier.fillMaxWidth()) {
                fila.forEach { s ->
                    val icono = remember(s.url, versionIconos) { Iconos.de(ctx, s.url) }
                    Column(
                        Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = { onClick(s) },
                                onLongClick = onLargo?.let { largo -> { largo(s) } }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .vidrio(RoundedCornerShape(20.dp), null, sombra = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconoSitio(icono, s.url, s.titulo, 34.dp)
                        }
                        Text(
                            s.titulo.ifBlank { hostDe(s.url) },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
                        )
                    }
                }
                repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* Manchas de color desenfocadas con los colores de tu fondo de pantalla: lo que el vidrio deja ver */
@Composable
fun FondoGotas(privada: Boolean) {
    val e = MaterialTheme.colorScheme
    val c1 = if (privada) Color(0xFF4B3AA8) else e.primary
    val c2 = if (privada) Color(0xFF1E5AA8) else e.tertiary
    val c3 = if (privada) Color(0xFF8A2E6E) else e.secondary
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset(x = (-60).dp, y = 40.dp)
                .size(300.dp)
                .background(Brush.radialGradient(listOf(c1.copy(alpha = 0.55f), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = 220.dp)
                .size(320.dp)
                .background(Brush.radialGradient(listOf(c2.copy(alpha = 0.45f), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = 40.dp, y = 60.dp)
                .size(width = 360.dp, height = 300.dp)
                .background(Brush.radialGradient(listOf(c3.copy(alpha = 0.40f), Color.Transparent)))
        )
    }
}
