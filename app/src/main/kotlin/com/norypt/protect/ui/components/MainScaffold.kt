package com.norypt.protect.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.norypt.protect.ui.theme.NoryptColors

enum class NavTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    TRIGGERS("Triggers", Icons.Filled.Settings),
    WIPE("Wipe", Icons.Filled.Delete),
    PROTECT("Protect", Icons.Filled.Lock),
}

@Composable
fun MainScaffold(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = NoryptColors.Bg,
        contentColor = NoryptColors.Text,
        bottomBar = {
            NavigationBar(
                containerColor = NoryptColors.Surface1,
                contentColor = NoryptColors.Text,
                tonalElevation = 0.dp,
            ) {
                NavTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { if (tab != selected) onSelect(tab) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (tab == selected) FontWeight.SemiBold
                                             else FontWeight.Medium,
                                maxLines = 1,
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NoryptColors.Accent,
                            selectedTextColor = NoryptColors.Accent,
                            unselectedIconColor = NoryptColors.Muted,
                            unselectedTextColor = NoryptColors.Muted,
                            indicatorColor = NoryptColors.AccentDim,
                        ),
                    )
                }
            }
        },
    ) { innerPadding -> content(innerPadding) }
}
