package com.yehu.liquid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlinx.coroutines.launch

/* Todas las pestañas en dos columnas, con los botones de vidrio flotando encima */
@Composable
fun CuadriculaPestanas() {
    val haze = remember { HazeState() }
    val privada = Navegador.cuadriculaPrivada
    val todas = if (privada) Navegador.privadas() else Navegador.normales()
    var seleccionando by remember { mutableStateOf(false) }
    val elegidas = remember { mutableStateListOf<Long>() }
    var buscando by remember { mutableStateOf(false) }
    var filtro by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val lista = if (filtro.isBlank()) todas else todas.filter {
        it.titulo.contains(filtro, true) || it.url.contains(filtro, true)
    }
    val estado = rememberLazyGridState()
    val esquema = MaterialTheme.colorScheme

    /* Empieza mostrando la pestaña actual */
    LaunchedEffect(privada) {
        val i = lista.indexOf(Navegador.actual)
        if (i >= 0) estado.scrollToItem(i)
    }

    fun terminar() {
        seleccionando = false
        elegidas.clear()
    }

    fun cerrarCuadricula() {
        val act = Navegador.actual
        if (act == null || act.privada != privada) {
            todas.lastOrNull()?.let { Navegador.seleccionar(it) } ?: Navegador.nueva(privada)
        }
        Navegador.verPestanas = false
    }

    Box(Modifier.fillMaxSize().background(esquema.background)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            FondoGotas(privada)
            if (todas.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(IC_PRIVADO, null, Modifier.size(48.dp), tint = esquema.onBackground.copy(alpha = 0.6f))
                    Text(
                        if (privada) "Navegación privada" else "Sin pestañas",
                        fontSize = 22.sp, fontWeight = FontWeight.Black, color = esquema.onBackground,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        if (privada) "Toca + para abrir una pestaña privada. No se guarda el historial y sus cookies se borran al cerrarla."
                        else "Toca + para abrir una pestaña.",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = esquema.onBackground.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            val barras = WindowInsets.statusBars.asPaddingValues()
            val nav = WindowInsets.navigationBars.asPaddingValues()
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = estado,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = barras.calculateTopPadding() + 76.dp,
                    bottom = nav.calculateBottomPadding() + 110.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(lista, key = { it.id }) { p ->
                    TarjetaPestana(
                        p,
                        actual = p == Navegador.actual,
                        seleccionando = seleccionando,
                        elegida = p.id in elegidas,
                        onClick = {
                            if (seleccionando) {
                                if (p.id in elegidas) elegidas.remove(p.id) else elegidas.add(p.id)
                            } else {
                                Navegador.seleccionar(p)
                                Navegador.verPestanas = false
                            }
                        },
                        onLargo = {
                            if (!seleccionando) {
                                seleccionando = true
                                elegidas.clear()
                                elegidas.add(p.id)
                            }
                        },
                        onCerrar = { Navegador.cerrar(p) }
                    )
                }
            }
        }

        /* Arriba: buscar entre las pestañas y el menú */
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (buscando) {
                val foco = remember { FocusRequester() }
                LaunchedEffect(Unit) { foco.requestFocus() }
                Row(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .vidrio(RoundedCornerShape(24.dp), haze)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(IC_BUSCAR, null, Modifier.size(18.dp), tint = esquema.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (filtro.isEmpty()) Text("Buscar pestañas", fontSize = 15.sp, color = esquema.onSurface.copy(alpha = 0.5f))
                        BasicTextField(
                            filtro, { filtro = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = Nunito, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = esquema.onSurface),
                            cursorBrush = SolidColor(esquema.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(foco)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                CirculoVidrio(IC_CERRAR, "Cerrar búsqueda", haze) {
                    buscando = false
                    filtro = ""
                }
            } else {
                CirculoVidrio(IC_BUSCAR, "Buscar pestañas", haze) { buscando = true }
                Spacer(Modifier.weight(1f))
                if (seleccionando) {
                    val todasElegidas = lista.isNotEmpty() && lista.all { it.id in elegidas }
                    Text(
                        if (todasElegidas) "Quitar selección" else "Seleccionar todo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = esquema.onSurface,
                        modifier = Modifier
                            .tocable {
                                if (todasElegidas) elegidas.clear()
                                else {
                                    elegidas.clear()
                                    elegidas.addAll(lista.map { it.id })
                                }
                            }
                            .vidrio(RoundedCornerShape(24.dp), haze)
                            .padding(horizontal = 18.dp, vertical = 13.dp)
                    )
                } else {
                    Box {
                        CirculoVidrio(IC_PUNTOS, "Más opciones", haze) { menu = true }
                    }
                }
            }
        }

        /* Menú de la cuadrícula */
        AnimatedVisibility(menu, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f))
                    .combinedClickableSinEfecto { menu = false }
            ) {
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 66.dp, end = 16.dp)
                        .width(250.dp)
                        .vidrio(RoundedCornerShape(26.dp), haze)
                        .padding(vertical = 8.dp)
                ) {
                    OpcionMenu(IC_LISTO, "Seleccionar pestañas") {
                        menu = false
                        seleccionando = true
                        elegidas.clear()
                    }
                    OpcionMenu(IC_LISTO, "Seleccionar todas") {
                        menu = false
                        seleccionando = true
                        elegidas.clear()
                        elegidas.addAll(lista.map { it.id })
                    }
                    OpcionMenu(IC_CERRAR, "Cerrar todas", rojo = true) {
                        menu = false
                        Navegador.cerrarVarias(todas)
                        if (!privada) Navegador.verPestanas = false
                    }
                }
            }
        }

        /* Abajo: +, Privado | N pestañas, y Listo */
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (seleccionando) {
                Text(
                    "Cancelar",
                    fontSize = 15.sp, fontWeight = FontWeight.Black, color = esquema.onSurface,
                    modifier = Modifier
                        .tocable { terminar() }
                        .vidrio(RoundedCornerShape(26.dp), haze)
                        .padding(horizontal = 22.dp, vertical = 15.dp)
                )
                Spacer(Modifier.weight(1f))
                val n = elegidas.size
                Text(
                    if (n == 0) "Elige pestañas" else if (n == 1) "Cerrar 1 pestaña" else "Cerrar $n pestañas",
                    fontSize = 15.sp, fontWeight = FontWeight.Black,
                    color = if (n == 0) esquema.onSurface.copy(alpha = 0.5f) else Color.White,
                    modifier = Modifier
                        .tocable {
                            if (n > 0) {
                                Navegador.cerrarVarias(todas.filter { it.id in elegidas })
                                terminar()
                            }
                        }
                        .vidrio(RoundedCornerShape(26.dp), haze, tinte = if (n == 0) null else Color(0xFFE5383B))
                        .padding(horizontal = 22.dp, vertical = 15.dp)
                )
            } else {
                CirculoVidrio(IC_MAS, "Pestaña nueva", haze, tamano = 54.dp) {
                    Navegador.nueva(privada)
                    Navegador.verPestanas = false
                }
                Spacer(Modifier.weight(1f))
                SelectorModo(privada, Navegador.normales().size, haze)
                Spacer(Modifier.weight(1f))
                CirculoVidrio(
                    IC_LISTO, "Listo", haze, tamano = 54.dp,
                    tinte = esquema.primary, colorIcono = esquema.onPrimary
                ) { cerrarCuadricula() }
            }
        }
    }
}

/* "Privado | N pestañas": una sola píldora de vidrio con una gota que se mueve al lado elegido */
@Composable
private fun SelectorModo(privada: Boolean, normales: Int, haze: HazeState) {
    val esquema = MaterialTheme.colorScheme
    val ancho = 118.dp
    val x by animateDpAsState(if (privada) 0.dp else ancho, tween(320), label = "modo")
    Box(
        Modifier
            .height(54.dp)
            .width(ancho * 2 + 8.dp)
            .vidrio(RoundedCornerShape(27.dp), haze)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = x.toPx() }
                .width(ancho)
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(esquema.onSurface.copy(alpha = 0.10f))
        )
        Row(Modifier.fillMaxSize()) {
            listOf(true to "Privado", false to if (normales == 1) "1 pestaña" else "$normales pestañas").forEach { (modo, txt) ->
                Box(
                    Modifier
                        .width(ancho)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .combinedClickableSinEfecto { Navegador.cuadriculaPrivada = modo },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        txt,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = esquema.onSurface.copy(alpha = if (modo == privada) 1f else 0.6f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/* Una pestaña de la cuadrícula: vista previa, la X para cerrarla y abajo su ícono y título.
   Deslízala hacia un lado para cerrarla. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TarjetaPestana(
    p: Pestana,
    actual: Boolean,
    seleccionando: Boolean,
    elegida: Boolean,
    onClick: () -> Unit,
    onLargo: () -> Unit,
    onCerrar: () -> Unit
) {
    val esquema = MaterialTheme.colorScheme
    val alcance = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val ancho = with(LocalDensity.current) { 160.dp.toPx() }
    val forma = RoundedCornerShape(26.dp)

    Column(
        Modifier
            .graphicsLayer {
                translationX = dx.value
                alpha = 1f - (abs(dx.value) / (ancho * 1.6f)).coerceIn(0f, 0.8f)
            }
            .pointerInput(p.id, seleccionando) {
                if (seleccionando) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        alcance.launch {
                            if (abs(dx.value) > ancho * 0.55f) {
                                dx.animateTo(if (dx.value > 0) ancho * 2.5f else -ancho * 2.5f, tween(180))
                                onCerrar()
                            } else {
                                dx.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { alcance.launch { dx.animateTo(0f) } }
                ) { c, d ->
                    c.consume()
                    alcance.launch { dx.snapTo(dx.value + d) }
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .then(
                    if (actual && !seleccionando) Modifier.border(3.dp, esquema.primary, forma)
                    else Modifier
                )
                .padding(if (actual && !seleccionando) 4.dp else 0.dp)
                .clip(RoundedCornerShape(if (actual && !seleccionando) 22.dp else 26.dp))
                .background(esquema.surface)
                .combinedClickable(onClick = onClick, onLongClick = onLargo)
        ) {
            val vista = p.vista
            if (vista != null && !p.enInicio) {
                Image(
                    vista, null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter
                )
            } else {
                /* Sin vista previa: Inicio, o una pestaña recuperada que aún no se abre */
                Box(Modifier.fillMaxSize()) {
                    FondoGotas(p.privada)
                    if (p.enInicio) {
                        Text(
                            if (p.privada) "Privada" else "Inicio",
                            fontSize = 22.sp, fontWeight = FontWeight.Black, color = esquema.onSurface,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        IconoSitio(
                            p.icono ?: Iconos.de(androidx.compose.ui.platform.LocalContext.current, p.url),
                            p.url, p.titulo, 56.dp,
                            Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
            if (!seleccionando) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .tocable { onCerrar() }
                        .size(30.dp)
                        .vidrio(CircleShape, null, sombra = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(IC_CERRAR, "Cerrar pestaña", Modifier.size(16.dp), tint = esquema.onSurface)
                }
            } else {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (elegida) esquema.primary else Color.Black.copy(alpha = 0.25f))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (elegida) Icon(IC_LISTO, null, Modifier.size(18.dp), tint = esquema.onPrimary)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (!p.enInicio) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                IconoSitio(p.icono ?: Iconos.de(ctx, p.url), p.url, p.titulo, 16.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                when {
                    p.enInicio -> if (p.privada) "Pestaña privada" else "Inicio"
                    else -> p.titulo.ifBlank { hostDe(p.url) }
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = esquema.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OpcionMenu(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, rojo: Boolean = false, onClick: () -> Unit) {
    val color = if (rojo) Color(0xFFE5383B) else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickableSinEfecto(onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(1f))
        Icon(icono, null, Modifier.size(20.dp), tint = color)
    }
}

/* Clic sin la onda de Material (el vidrio ya reacciona) */
@Composable
fun Modifier.combinedClickableSinEfecto(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
