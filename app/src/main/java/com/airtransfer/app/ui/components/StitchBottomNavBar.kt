package com.airtransfer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtransfer.app.ui.theme.*

@Composable
fun StitchBottomNavBar(
    selectedTab: String = "radar",
    onTabSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(StitchSurface.copy(alpha = 0.95f))
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(
            icon = Icons.Default.NearMe,
            label = "Radar",
            isSelected = selectedTab == "radar",
            onClick = { onTabSelected("radar") }
        )
        NavItem(
            icon = Icons.Default.SyncAlt,
            label = "Transfers",
            isSelected = selectedTab == "transfers",
            onClick = { onTabSelected("transfers") }
        )
        NavItem(
            icon = Icons.Default.History,
            label = "History",
            isSelected = selectedTab == "history",
            onClick = { onTabSelected("history") }
        )
        NavItem(
            icon = Icons.Default.Devices,
            label = "Devices",
            isSelected = selectedTab == "devices",
            onClick = { onTabSelected("devices") }
        )
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) StitchPrimary else StitchOnSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) StitchPrimary else StitchOnSurfaceVariant
        )
    }
}
