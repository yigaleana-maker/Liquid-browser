package com.yehu.liquid

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/* Vidrio líquido: lo de atrás se ve desenfocado (Android 12+), con un tinte suave y un borde de luz.
   Sin HazeState (encima de una página web) es un vidrio esmerilado casi opaco. */
@Composable
fun Modifier.vidrio(forma: Shape, haze: HazeState?, tinte: Color? = null, sombra: Dp = 10.dp): Modifier {
    val oscuro = LocalOscuro.current
    val base = tinte ?: if (oscuro) Color(0xFF2A2B31) else Color.White
    val borde = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (oscuro) 0.24f else 0.95f),
            Color.White.copy(alpha = if (oscuro) 0.04f else 0.30f)
        )
    )
    val fondo = MaterialTheme.colorScheme.background
    var m = this
    if (sombra > 0.dp) {
        m = m.shadow(
            sombra, forma, clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = if (oscuro) 0.5f else 0.16f)
        )
    }
    m = m.clip(forma)
    m = if (haze != null && Build.VERSION.SDK_INT >= 31) {
        m.hazeEffect(
            haze,
            HazeStyle(
                backgroundColor = fondo,
                tint = HazeTint(base.copy(alpha = if (tinte != null) 0.80f else if (oscuro) 0.55f else 0.50f)),
                blurRadius = 24.dp,
                noiseFactor = 0f
            )
        )
    } else {
        m.background(base.copy(alpha = if (tinte != null) 1f else 0.94f))
    }
    /* Brillo arriba, como el borde de una gota */
    m = m.background(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (oscuro) 0.06f else 0.22f), Color.Transparent)
        )
    )
    return m.border(1.dp, borde, forma)
}

/* Se encoge un poco al tocarlo, con rebote */
@Composable
fun Modifier.tocable(onClick: () -> Unit): Modifier {
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val s by animateFloatAsState(if (presionado) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "tocar")
    return this.scale(s).clickable(interactionSource = fuente, indication = null, onClick = onClick)
}

/* Botón redondo de vidrio con un ícono */
@Composable
fun CirculoVidrio(
    icono: ImageVector,
    descripcion: String,
    haze: HazeState?,
    modifier: Modifier = Modifier,
    tamano: Dp = 48.dp,
    activo: Boolean = true,
    tinte: Color? = null,
    colorIcono: Color? = null,
    onClick: () -> Unit
) {
    Box(
        modifier
            .tocable { if (activo) onClick() }
            .size(tamano)
            .vidrio(CircleShape, haze, tinte),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icono,
            descripcion,
            tint = (colorIcono ?: MaterialTheme.colorScheme.onSurface).copy(alpha = if (activo) 1f else 0.3f),
            modifier = Modifier.size(tamano * 0.5f)
        )
    }
}

/* El ícono del sitio, o su inicial en un cuadrito de color si todavía no lo tenemos */
@Composable
fun IconoSitio(icono: Bitmap?, url: String, titulo: String, tamano: Dp, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(tamano * 0.28f)
    if (icono != null) {
        val img = remember(icono) { icono.asImageBitmap() }
        Image(img, null, modifier.size(tamano).clip(forma))
    } else {
        val host = hostDe(url)
        val letra = (titulo.ifBlank { host }).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '•'
        val color = colorDe(host.ifEmpty { titulo })
        Box(modifier.size(tamano).clip(forma).background(color), contentAlignment = Alignment.Center) {
            Text(letra.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = (tamano.value * 0.5f).sp)
        }
    }
}

private val COLORES = listOf(
    Color(0xFF1F6BFF), Color(0xFF16A35F), Color(0xFFFF8A1F), Color(0xFFE5407A),
    Color(0xFF7A5AF8), Color(0xFF0FA3B1), Color(0xFFD9483B), Color(0xFF5E7A2E)
)

fun colorDe(s: String): Color = COLORES[(s.hashCode() and 0x7fffffff) % COLORES.size]
