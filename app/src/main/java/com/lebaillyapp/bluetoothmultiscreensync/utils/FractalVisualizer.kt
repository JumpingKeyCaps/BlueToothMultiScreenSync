package com.lebaillyapp.bluetoothmultiscreensync.utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 *
 * ### FractalVisualizer
 *
 * Affiche une fractale animée dans un `Canvas`, avec effets lumineux de type néon.
 *
 * Cette composable est hautement paramétrable pour permettre des visualisations dynamiques
 * et interactives (spirales, battements, figures organiques animées).
 *
 * @param modifier Modificateur d’agencement (taille, padding, etc.).
 * @param glowColor Couleur du glow principal (ex. vert néon).
 * @param baseAlpha Opacité maximale des points les plus proches.
 * @param glowAlphaMultiplier Intensité relative du halo néon (autour du point).
 * @param maxRadius Rayon maximal (en dp) de la fractale.
 * @param pointCount Nombre total de points dessinés (détermine la densité).
 * @param animationDurationMs Durée d’un cycle d’animation (ms).
 * @param isAnimating Active/désactive l’animation (sinon figée à l’état actuel).
 * @param timeOffset Décalage temporel externe, utile pour moduler dynamiquement la forme via ViewModel ou LFO.
 * @param spiralFrequency Fréquence des bras spiraux (plus élevé = plus de spirales).
 * @param internalOscillationFreq Fréquence interne des oscillations (forme + battement).
 * @param rotationSpeed Vitesse de rotation globale de la fractale.
 */
@Composable
fun FractalVisualizer(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF00FF41),
    baseAlpha: Float = 1f,
    glowAlphaMultiplier: Float = 0.3f,
    maxRadius: Dp = 40.dp,
    pointCount: Int = 80,
    animationDurationMs: Int = 30000,
    isAnimating: Boolean = true,
    timeOffset: Float = 0f,
    spiralFrequency: Float = 8f,
    internalOscillationFreq: Float = 12f,
    rotationSpeed: Float = 1.5f
) {
    val animatedTime by rememberInfiniteTransition(label = "fractalAnimation").animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timeFractal"
    )

    val time = if (isAnimating) animatedTime + timeOffset else timeOffset

    Canvas(modifier = modifier) {
        drawFractal(
            time = time,
            glowColor = glowColor,
            baseAlpha = baseAlpha,
            glowAlphaMultiplier = glowAlphaMultiplier,
            maxRadius = maxRadius.toPx(),
            pointCount = pointCount,
            spiralFrequency = spiralFrequency,
            internalOscillationFreq = internalOscillationFreq,
            rotationSpeed = rotationSpeed
        )
    }
}

private fun DrawScope.drawFractal(
    time: Float,
    glowColor: Color,
    baseAlpha: Float,
    glowAlphaMultiplier: Float,
    maxRadius: Float,
    pointCount: Int,
    spiralFrequency: Float,
    internalOscillationFreq: Float,
    rotationSpeed: Float
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f

    for (i in 0..pointCount) {
        val t = i / pointCount.toFloat()
        val angle = t * spiralFrequency * PI + time * rotationSpeed
        val radius = maxRadius * t * sin(t * internalOscillationFreq + time * 2f)
        val x = centerX + radius * cos(angle).toFloat()
        val y = centerY + radius * sin(angle).toFloat()
        val alpha = (1 - t) * baseAlpha
        val dotSize = (3 - t * 2).dp.toPx()

        drawCircle(glowColor.copy(alpha = alpha * glowAlphaMultiplier), dotSize * 2, Offset(x, y))
        drawCircle(glowColor.copy(alpha = alpha), dotSize, Offset(x, y))
    }
}