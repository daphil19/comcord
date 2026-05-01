package dev.phillipslabs.comcord.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.phillipslabs.comcord.design.ComcordColors
import dev.phillipslabs.comcord.rooms.SpaceSummary

@Composable
fun LeftRail(
    compact: Boolean,
    spaces: List<SpaceSummary>,
    selectedSpaceId: String,
    onSelectSpace: (String) -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(if (compact) 60.dp else 76.dp)
                .background(ComcordColors.SurfacePrimary)
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        spaces.forEach { space ->
            RailButton(
                label = space.name.take(2).uppercase(),
                active = space.id == selectedSpaceId,
                onClick = { onSelectSpace(space.id) },
            )
        }
        Spacer(Modifier.weight(1f))
        RailButton(label = "LO", active = false, onClick = onLogout)
    }
}

@Composable
private fun RailButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(if (active) 16.dp else 24.dp))
                .background(if (active) ComcordColors.Accent else ComcordColors.SurfaceTertiary)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = ComcordColors.TextStrong, fontWeight = FontWeight.Bold)
    }
}
