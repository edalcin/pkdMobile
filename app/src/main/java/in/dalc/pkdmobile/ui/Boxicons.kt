package `in`.dalc.pkdmobile.ui

import android.content.Context
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.dalc.pkdmobile.R

/**
 * Boxicons 2.1.4 (the PKD's icon set, MIT, assets/boxicons-LICENSE.txt) as a font. assets/boxicons.txt
 * maps each class (`bx-…`, `bxs-…`, `bxl-…`) to its code point, generated from boxicons.css.
 */
object Boxicons {
    private val font = FontFamily(Font(R.font.boxicons))
    private var map: Map<String, String>? = null

    private fun map(context: Context): Map<String, String> = map ?: context.assets.open("boxicons.txt").bufferedReader().useLines { lines ->
        lines.filter { '=' in it }.associate { line ->
            val (name, hex) = line.split('=')
            name to String(Character.toChars(hex.toInt(16)))
        }
    }.also { map = it }

    /** The glyph for a Boxicons class, or null when this version does not have it. */
    fun glyph(context: Context, name: String): String? = map(context)[name.removePrefix("bx ").trim()]

    @Composable
    fun Icon(name: String, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = LocalContentColor.current) {
        val glyph = glyph(LocalContext.current, name) ?: glyph(LocalContext.current, "bx-file")!!
        val fontSize = with(LocalDensity.current) { size.toSp() }
        Text(
            glyph, color = tint, fontFamily = font, fontSize = fontSize, lineHeight = fontSize, textAlign = TextAlign.Center,
            // The glyph is a private-use character: screen readers get only the description.
            modifier = modifier.clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
        )
    }
}
