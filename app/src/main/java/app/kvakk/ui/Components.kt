package app.kvakk.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kvakk.R

/**
 * A chunky, toy-like button: a coloured face sitting on a darker edge that shows below it.
 * Pressing pushes the face down onto the edge.
 */
@Composable
fun ToySurface(
    onClick: (() -> Unit)?,
    color: Color,
    edge: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    depth: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depthPx = with(LocalDensity.current) { depth.toPx() }
    val push by animateFloatAsState(if (pressed) depthPx * 0.7f else 0f, label = "press")
    val click = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Box(modifier.then(click), propagateMinConstraints = true) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = depth)
                .background(edge, shape),
        )
        Box(
            Modifier
                .padding(bottom = depth)
                .graphicsLayer { translationY = push }
                .background(color, shape)
                .padding(contentPadding),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/** Rounded pill in the top bar (sleep timer, volume, sound output). */
@Composable
fun Pill(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = LocalKvakkColors.current
    ToySurface(
        onClick = onClick,
        color = colors.surface,
        edge = colors.surfaceEdge,
        shape = CircleShape,
        depth = 4.dp,
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    size: Dp,
    color: Color,
    edge: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
) {
    ToySurface(
        onClick = onClick,
        color = color,
        edge = edge,
        shape = CircleShape,
        depth = if (size >= 64.dp) 6.dp else 4.dp,
        modifier = modifier.size(size),
    ) {
        Icon(icon, contentDescription, tint = iconColor, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun Duck(modifier: Modifier = Modifier, sleeping: Boolean = LocalKvakkColors.current.isNight) {
    Image(
        painter = painterResource(if (sleeping) R.drawable.ic_duck_sleeping else R.drawable.ic_duck),
        contentDescription = null,
        modifier = modifier,
    )
}

/** Fires once the pointer has been held down for [durationMs] without lifting. */
fun Modifier.holdToActivate(durationMs: Long, onActivate: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            val released = withTimeoutOrNull(durationMs) {
                waitForUpOrCancellation()
                true
            }
            if (released == null) onActivate()
        }
    },
)

/** Darker shade of [color] for a toy button's edge. */
fun edgeFor(color: Color): Color = Color(
    red = color.red * 0.78f,
    green = color.green * 0.78f,
    blue = color.blue * 0.78f,
    alpha = color.alpha,
)
