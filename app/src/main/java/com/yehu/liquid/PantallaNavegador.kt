package com.yehu.liquid

import org.mozilla.geckoview.GeckoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlinx.coroutines.launch

/* El navegador: la página (o Inicio) arriba y la barra de vidrio abajo */
@Composable
fun PantallaNavegador(p: Pestana) {
    val densidad = LocalDensity.current
    val teclado = WindowInsets.ime.getBottom(densidad) > 0
    val haze = remember { HazeState() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (p.enInicio) {
            /* Inicio es de Compose, así que la barra lo desenfoca de verdad */
            PaginaInicio(
                p,
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
            )
        } else {
            /* En pantalla completa (videos) la página ocupa todo y la barra se esconde */
            val completa = Navegador.pantallaCompleta
            Box(
                if (completa) Modifier.fillMaxSize().background(Color.Black)
                else Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = if (teclado) 0.dp else ALTO_BARRA)
                    .windowInsetsPadding(if (teclado) WindowInsets.ime else WindowInsets.navigationBars)
            ) {
                AndroidView(
                    factory = { ctx -> GeckoView(ctx).also { Navegador.vistaActual = it } },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { v ->
                        v.releaseSession()
                        if (Navegador.vistaActual == v) Navegador.vistaActual = null
                    },
                    update = { v ->
                        Navegador.vistaActual = v
                        val s = p.sesion
                        if (s != null && v.session != s) v.setSession(s)
                    }
                )
                BarraProgreso(p.progreso, Modifier.align(Alignment.TopCenter))
            }
        }

        /* Con el teclado abierto (escribiendo en una página) la barra se esconde */
        if ((!teclado || Navegador.buscandoEnPagina) && !(Navegador.pantallaCompleta && !p.enInicio)) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(if (teclado) WindowInsets.ime else WindowInsets.navigationBars)
            ) {
                if (Navegador.buscandoEnPagina) BarraBuscarEnPagina(if (p.enInicio) haze else null)
                else BarraDireccion(p, if (p.enInicio) haze else null)
            }
        }
    }
}

val ALTO_BARRA = 72.dp

@Composable
private fun BarraProgreso(progreso: Int, modifier: Modifier) {
    val visible = progreso in 1..99
    val f by animateFloatAsState(progreso / 100f, label = "progreso")
    val alfa by animateFloatAsState(if (visible) 1f else 0f, label = "alfa")
    Box(modifier.fillMaxWidth().height(3.dp).graphicsLayer { alpha = alfa }) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(f)
                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/* La barra de abajo: atrás, (adelante), la píldora de la dirección, las pestañas y el menú.
   Desliza la píldora a los lados para cambiar de pestaña, o hacia arriba para verlas todas. */
@Composable
private fun BarraDireccion(p: Pestana, haze: HazeState?) {
    val alcance = rememberCoroutineScope()
    val desliz = remember { Animatable(0f) }
    val densidad = LocalDensity.current
    val umbral = with(densidad) { 90.dp.toPx() }
    val lista = if (p.privada) Navegador.privadas() else Navegador.normales()
    val numero = lista.size

    Row(
        Modifier
            .fillMaxWidth()
            .height(ALTO_BARRA)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CirculoVidrio(
            IC_ATRAS, "Atrás", haze,
            activo = p.puedeAtras || (!p.enInicio && p.volverAInicio)
        ) { Navegador.atras() }
        AnimatedVisibility(
            visible = p.puedeAdelante,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            CirculoVidrio(IC_ADELANTE, "Adelante", haze) { Navegador.adelante() }
        }

        /* La píldora de la dirección */
        var arrastreY by remember { mutableStateOf(0f) }
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .graphicsLayer { translationX = desliz.value }
                .pointerInput(p.id) {
                    detectDragGestures(
                        onDragStart = { arrastreY = 0f },
                        onDragEnd = {
                            val dx = desliz.value
                            when {
                                arrastreY < -umbral * 0.6f && abs(dx) < umbral -> {
                                    Navegador.actual?.let { Navegador.capturar(it) }
                                    Navegador.cuadriculaPrivada = p.privada
                                    Navegador.verPestanas = true
                                }
                                dx < -umbral -> cambiarPestana(p, +1)
                                dx > umbral -> cambiarPestana(p, -1)
                            }
                            alcance.launch { desliz.animateTo(0f) }
                        },
                        onDragCancel = { alcance.launch { desliz.animateTo(0f) } }
                    ) { cambio, d ->
                        cambio.consume()
                        arrastreY += d.y
                        alcance.launch { desliz.snapTo(desliz.value + d.x * 0.6f) }
                    }
                }
                .tocable { Navegador.editando = true }
                .vidrio(RoundedCornerShape(26.dp), haze),
            contentAlignment = Alignment.Center
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!p.enInicio && p.segura) {
                    Icon(IC_CANDADO, "Conexión segura", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    Spacer(Modifier.width(6.dp))
                } else if (p.enInicio) {
                    Icon(IC_BUSCAR, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    when {
                        p.enInicio -> if (p.privada) "Buscar en privado" else "Buscar o escribir dirección"
                        else -> busquedaDe(p.url) ?: hostDe(p.url).ifEmpty { p.url }
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = if (p.enInicio) FontWeight.SemiBold else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (p.enInicio) 0.55f else 1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (p.enInicio) TextAlign.Start else TextAlign.Center
                )
                if (!p.enInicio) {
                    Icon(
                        if (p.progreso in 1..99) IC_CERRAR else IC_RECARGAR,
                        if (p.progreso in 1..99) "Detener" else "Recargar",
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .tocable { Navegador.recargar() }
                            .padding(5.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            /* Progreso dentro de la píldora, abajo */
            if (p.progreso in 1..99) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(p.progreso / 100f)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        /* Botón de pestañas con el número */
        Box(
            Modifier
                .tocable {
                    Navegador.actual?.let { Navegador.capturar(it) }
                    Navegador.cuadriculaPrivada = p.privada
                    Navegador.verPestanas = true
                }
                .size(48.dp)
                .vidrio(CircleShape, haze),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (numero > 99) ":D" else "$numero",
                    fontSize = if (numero > 9) 10.sp else 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        CirculoVidrio(IC_PUNTOS, "Menú", haze) { Navegador.menuAbierto = true }
    }
}

/* Pasar a la pestaña de al lado; al final de la lista se abre una nueva */
private fun cambiarPestana(p: Pestana, dir: Int) {
    val lista = if (p.privada) Navegador.privadas() else Navegador.normales()
    val i = lista.indexOf(p) + dir
    when {
        i in lista.indices -> Navegador.seleccionar(lista[i])
        dir > 0 && !p.enInicio -> Navegador.nueva(p.privada)
    }
}

/* Buscar texto dentro de la página */
@Composable
private fun BarraBuscarEnPagina(haze: HazeState?) {
    var texto by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    val (activo, total) = Navegador.resultadosBusqueda
    Row(
        Modifier
            .fillMaxWidth()
            .height(ALTO_BARRA)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .vidrio(RoundedCornerShape(26.dp), haze)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(IC_BUSCAR, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (texto.isEmpty()) {
                    Text("Buscar en la página", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                BasicTextField(
                    texto,
                    {
                        texto = it
                        Navegador.buscarEnPagina(it)
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Nunito, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { Navegador.siguienteResultado(true) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(foco)
                )
            }
            if (texto.isNotEmpty()) {
                Text(
                    "$activo/$total",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        CirculoVidrio(IC_FLECHA_ARRIBA, "Anterior", haze, tamano = 44.dp) { Navegador.siguienteResultado(false) }
        CirculoVidrio(IC_FLECHA_ABAJO, "Siguiente", haze, tamano = 44.dp) { Navegador.siguienteResultado(true) }
        CirculoVidrio(IC_CERRAR, "Cerrar", haze, tamano = 44.dp) { Navegador.terminarBusqueda() }
    }
}
