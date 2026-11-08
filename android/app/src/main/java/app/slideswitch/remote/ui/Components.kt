package app.slideswitch.remote.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.slideswitch.remote.ui.theme.AppTheme

@Composable
fun Label(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.label,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text, modifier, style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** iOS Settings-style icon: white glyph on a gradient rounded square. */
@Composable
fun GradientIcon(glyph: ImageVector, gradient: Brush, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Image(glyph, null, Modifier.size(size * 0.6f), colorFilter = ColorFilter.tint(Color.White))
    }
}

/** Shrinks slightly while pressed, springs back — the iOS button feel (no ripple). */
@Composable
fun Modifier.pressable(enabled: Boolean = true, haptic: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    return this
        .scale(scale)
        .alpha(if (enabled) 1f else 0.4f)
        .clickable(interaction, indication = null, enabled = enabled) {
            if (haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
}

/** Large rounded, gradient-filled button with a bold label (used for Next / Previous). */
@Composable
fun BigButton(
    title: String,
    glyph: ImageVector,
    gradient: Brush,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyphSize: Dp = 56.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .pressable(enabled, onClick = onClick)
            .clip(RoundedCornerShape(32.dp))
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(glyph, null, Modifier.size(glyphSize), colorFilter = ColorFilter.tint(Color.White))
            Spacer(Modifier.height(8.dp))
            Label(title, AppTheme.type.title1, color = Color.White)
            if (subtitle != null) Label(subtitle, AppTheme.type.footnote, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/** Square tile with a gradient icon above a label (Start / Black / End). */
@Composable
fun TileButton(
    title: String,
    glyph: ImageVector,
    gradient: Brush,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .pressable(enabled, onClick = onClick)
            .clip(RoundedCornerShape(24.dp))
            .background(AppTheme.colors.well)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GradientIcon(glyph, gradient, size = 48.dp)
        Spacer(Modifier.height(10.dp))
        Label(title, AppTheme.type.headline)
    }
}

/** Full-width filled pill button, iOS "prominent" style. */
@Composable
fun PillButton(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.accent,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .pressable(enabled, onClick = onClick)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Label(title, AppTheme.type.headline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = textColor)
    }
}

/** Inset grouped section (iOS Settings style) with an optional header and footer. */
@Composable
fun InsetGroup(
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (header != null) {
            Label(
                header.uppercase(), AppTheme.type.footnote, color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(start = 20.dp, bottom = 7.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppTheme.colors.card),
            content = content,
        )
        if (footer != null) {
            Label(
                footer, AppTheme.type.footnote, color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp),
            )
        }
    }
}

@Composable
fun GroupRow(
    title: String,
    glyph: ImageVector,
    gradient: Brush,
    subtitle: String? = null,
    showDivider: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientIcon(glyph, gradient)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(title, AppTheme.type.body, maxLines = 1)
                if (subtitle != null) Label(subtitle, AppTheme.type.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 1)
            }
            if (trailing != null) trailing() else {
                Image(Glyphs.SmallChevron, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(AppTheme.colors.secondaryLabel))
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .padding(start = 60.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(AppTheme.colors.separator),
            )
        }
    }
}

/** Connection status capsule with a colored dot. */
@Composable
fun StatusPill(text: String, dot: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(AppTheme.colors.fill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Label(text, AppTheme.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
    }
}

/**
 * iOS-style segmented control: a grey capsule with a sliding, springy selection pill.
 * Used for the bottom tab bar (Connect / Settings) and the appearance picker.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    textStyle: TextStyle = AppTheme.type.subheadline,
    icons: List<ImageVector>? = null,
) {
    val colors = AppTheme.colors
    val haptics = LocalHapticFeedback.current
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.well)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by androidx.compose.animation.core.animateDpAsState(
            segment * selected,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.wellSelected),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, title ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (index != selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        icons?.getOrNull(index)?.let { icon ->
                            Image(icon, null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(colors.label))
                            Spacer(Modifier.height(3.dp))
                        }
                        Label(
                            title,
                            textStyle.copy(fontWeight = if (index == selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal),
                        )
                    }
                }
            }
        }
    }
}
