package app.kvakk.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    /** Tile colour taken from the icon, dark enough for white text. */
    val color: Color,
)

object InstalledApps {
    private const val ICON_PX = 192

    /** Every app with a launcher icon except Kvakk itself. Slow; call off the main thread. */
    fun launchable(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                val packageName = it.activityInfo.packageName
                val bitmap = it.loadIcon(pm).toBitmap(ICON_PX, ICON_PX)
                AppEntry(
                    packageName = packageName,
                    label = it.loadLabel(pm).toString(),
                    icon = bitmap.asImageBitmap(),
                    color = tileColor(bitmap, packageName),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun tileColor(icon: Bitmap, packageName: String): Color {
        val palette = Palette.from(icon).generate()
        val swatch = palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch
        val hsl = FloatArray(3)
        if (swatch != null && swatch.hsl[1] > 0.25f) {
            swatch.hsl.copyInto(hsl)
        } else {
            // Grey or white icons: pick a friendly colour that stays the same for each app.
            hsl[0] = FALLBACK_HUES[Math.floorMod(packageName.hashCode(), FALLBACK_HUES.size)]
            hsl[1] = 0.75f
        }
        hsl[1] = hsl[1].coerceIn(0.55f, 0.9f)
        hsl[2] = hsl[2].coerceIn(0.4f, 0.58f)
        // Yellows and greens are bright even at mid lightness; darken until white text reads well.
        while (ColorUtils.calculateLuminance(ColorUtils.HSLToColor(hsl)) > MAX_TILE_LUMINANCE && hsl[2] > 0.25f) {
            hsl[2] -= 0.02f
        }
        return Color(ColorUtils.HSLToColor(hsl))
    }

    private const val MAX_TILE_LUMINANCE = 0.3

    private val FALLBACK_HUES = floatArrayOf(8f, 220f, 160f, 265f, 30f, 330f, 190f)

    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
