package org.orbiliq.feature.inbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Small, repo-native line icons; labels live on their enclosing Material controls. */
@Composable
internal fun MessengerIcon(name: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val scale = size.width / 24f
        val path = PathParser().parsePathString(when (name) {
            "Chats" -> "M5,3 L19,3 Q22,3 22,6 L22,15 Q22,18 19,18 L9,18 L3,22 L3,6 Q3,3 5,3 Z M7,8 L18,8 M7,12 L15,12"
            "Menschen" -> "M3,21 L3,18 Q3,13 9,13 Q15,13 15,18 L15,21 M17,13 Q22,13 22,18 L22,21"
            "Konten" -> "M4,8 L4,5 Q4,3 6,3 L20,3 L20,19 L7,19 Q4,19 4,16 L4,8 Z M4,8 L20,8 M15,12 L22,12 L22,16 L15,16 Z"
            "Send" -> "M12,21 L12,3 M5,10 L12,3 L19,10"
            else -> "M12,4 L12,20 M4,12 L20,12"
        }).toPath()
        path.transform(androidx.compose.ui.graphics.Matrix().apply { scale(scale, scale) })
        drawPath(path, color, style = Stroke(width = 1.8f * scale, cap = StrokeCap.Round))
        if (name == "Menschen") {
            drawCircle(color, 3.5f * scale, Offset(9f * scale, 6.5f * scale), style = Stroke(1.8f * scale))
            drawCircle(color, 2.5f * scale, Offset(19f * scale, 7f * scale), style = Stroke(1.8f * scale))
        }
    }
}
