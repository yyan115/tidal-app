package io.github.yyan115.undertow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OpenTuneRail(titles: List<String>, icons: List<ImageVector>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.width(84.dp).fillMaxHeight().safeDrawingPadding().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        titles.forEachIndexed { index, title ->
            val color = if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("nav-$index").selectable(selected == index,
                role = Role.Tab, onClick = { onSelect(index) }), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(icons[index], null, Modifier.size(22.dp), tint = color)
                Text(title, Modifier.padding(top = 6.dp), fontFamily = InterfaceFont, fontSize = 9.sp, color = color)
            }
        }
    }
}

@Composable
fun OpenTuneNavigation(titles: List<String>, icons: List<ImageVector>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
        titles.forEachIndexed { index, title ->
            val active = index == selected
            Column(Modifier.weight(1f).heightIn(min = 58.dp).testTag("nav-$index")
                .selectable(active, role = Role.Tab, onClick = { onSelect(index) }),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(icons[index], null, Modifier.size(22.dp), tint = color)
                Text(title, Modifier.padding(top = 6.dp), fontFamily = InterfaceFont, fontSize = 9.sp, lineHeight = 13.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal, color = color)
            }
        }
    }
}

@Composable
fun FilterTab(selected: Boolean, onClick: () -> Unit, label: String) {
    Column(Modifier.width(IntrinsicSize.Max).selectable(selected, role = Role.Tab, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontFamily = InterfaceFont, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
    }
}
