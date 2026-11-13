package com.yehu.liquid

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* El menú ⋯: cuatro botones grandes arriba y la lista de opciones abajo */
@Composable
fun MenuNavegador(p: Pestana) {
    val ctx = LocalContext.current
    val esquema = MaterialTheme.colorScheme
    val v = Favoritos.version
    val esFavorito = remember(v, p.url) { !p.enInicio && Favoritos.es(ctx, p.url) }
    fun cerrar() {
        Navegador.menuAbierto = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .combinedClickableSinEfecto { cerrar() }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp)
                .fillMaxWidth()
                .vidrio(RoundedCornerShape(30.dp), null)
                .combinedClickableSinEfecto { }
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BotonMenu(IC_ADELANTE, "Adelante", p.puedeAdelante, Modifier.weight(1f)) {
                    cerrar()
                    Navegador.adelante()
                }
                BotonMenu(IC_RECARGAR, "Recargar", !p.enInicio, Modifier.weight(1f)) {
                    cerrar()
                    p.sesion?.reload()
                }
                BotonMenu(IC_COMPARTIR, "Compartir", !p.enInicio, Modifier.weight(1f)) {
                    cerrar()
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, p.url)
                        .putExtra(Intent.EXTRA_SUBJECT, p.titulo)
                    ctx.startActivity(Intent.createChooser(i, null))
                }
                BotonMenu(if (esFavorito) IC_ESTRELLA else IC_ESTRELLA_BORDE, if (esFavorito) "Favorito" else "Guardar", !p.enInicio, Modifier.weight(1f)) {
                    val ahora = Favoritos.alternar(ctx, p.url, p.titulo)
                    p.icono?.let { Iconos.guardar(ctx, p.url, it) }
                    Toast.makeText(ctx, if (ahora) "Añadido a favoritos" else "Quitado de favoritos", Toast.LENGTH_SHORT).show()
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 18.dp), color = esquema.onSurface.copy(alpha = 0.08f))
            if (!p.enInicio) {
                FilaMenu(IC_ENCONTRAR, "Buscar en la página") {
                    cerrar()
                    Navegador.buscandoEnPagina = true
                }
                FilaMenu(IC_ESCRITORIO, if (p.escritorio) "Versión para móvil" else "Versión de escritorio") {
                    cerrar()
                    Navegador.alternarEscritorio(p)
                }
                FilaMenu(IC_INICIO, "Ir a Inicio") {
                    cerrar()
                    Navegador.irAInicio(p)
                }
            }
            FilaMenu(IC_MAS, "Pestaña nueva") {
                cerrar()
                Navegador.nueva(false)
            }
            FilaMenu(IC_PRIVADO, "Pestaña privada nueva") {
                cerrar()
                Navegador.nueva(true)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 18.dp), color = esquema.onSurface.copy(alpha = 0.08f))
            /* Bloqueador de anuncios (uBlock Origin) */
            val bloqueo = Extensiones.bloqueador
            FilaMenu(IC_ESCUDO, textoExtension(bloqueo.estado, "Bloqueador de anuncios")) { bloqueo.alternar() }
            val ajustes = bloqueo.ajustes
            if (bloqueo.estado == ExtensionFirefox.Estado.ACTIVO && ajustes != null) {
                FilaMenu(IC_AJUSTES, "Ajustes de uBlock Origin") {
                    cerrar()
                    Navegador.nueva(false, ajustes)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 18.dp), color = esquema.onSurface.copy(alpha = 0.08f))
            /* Modo oscuro: el de la app y el de las páginas (Dark Reader) */
            FilaMenu(IC_TEMA, "Tema: ${Apariencia.tema.nombre}") { Apariencia.siguiente(ctx, Motor.de(ctx)) }
            val oscuro = Extensiones.oscurecedor
            FilaMenu(IC_LUNA, textoExtension(oscuro.estado, "Oscurecer todas las páginas")) { oscuro.alternar() }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 18.dp), color = esquema.onSurface.copy(alpha = 0.08f))
            FilaMenu(IC_ESTRELLA, "Favoritos") {
                cerrar()
                Navegador.lista = Lista.FAVORITOS
            }
            FilaMenu(IC_HISTORIAL, "Historial") {
                cerrar()
                Navegador.lista = Lista.HISTORIAL
            }
            FilaMenu(IC_DESCARGAS, "Descargas") {
                cerrar()
                Navegador.lista = Lista.DESCARGAS
            }
        }
    }
}

@Composable
private fun BotonMenu(icono: ImageVector, texto: String, activo: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val esquema = MaterialTheme.colorScheme
    Column(
        modifier
            .tocable { if (activo) onClick() }
            .height(74.dp)
            .vidrio(RoundedCornerShape(20.dp), null, tinte = esquema.onSurface.copy(alpha = 0.06f).compositeOver(esquema.surface), sombra = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icono, texto, Modifier.size(24.dp), tint = esquema.onSurface.copy(alpha = if (activo) 1f else 0.3f))
        Text(
            texto,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = esquema.onSurface.copy(alpha = if (activo) 0.85f else 0.3f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun FilaMenu(icono: ImageVector, texto: String, onClick: () -> Unit) {
    val esquema = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickableSinEfecto(onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icono, null, Modifier.size(22.dp), tint = esquema.onSurface.copy(alpha = 0.8f))
        Text(
            texto,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
            color = esquema.onSurface,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

private fun Color.compositeOver(fondo: Color): Color {
    val a = alpha
    return Color(
        red = red * a + fondo.red * (1 - a),
        green = green * a + fondo.green * (1 - a),
        blue = blue * a + fondo.blue * (1 - a),
        alpha = 1f
    )
}

private fun textoExtension(e: ExtensionFirefox.Estado, nombre: String): String = when (e) {
    ExtensionFirefox.Estado.ACTIVO -> "$nombre: sí"
    ExtensionFirefox.Estado.APAGADO -> "$nombre: no"
    ExtensionFirefox.Estado.INSTALANDO, ExtensionFirefox.Estado.BUSCANDO -> "$nombre: instalando…"
    ExtensionFirefox.Estado.ERROR -> "$nombre: toca para reintentar"
}
