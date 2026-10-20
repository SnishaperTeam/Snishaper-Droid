package com.snishaper.android.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector
import com.snishaper.android.R

/**
 * Navigation destinations, in the exact order of the desktop sidebar
 * (`SniShaper-main/frontend/src/components/Sidebar.tsx:22-32`).
 *
 * There is intentionally no CA / certificate entry (Android build keeps zero CA surface).
 */
enum class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard("dashboard", R.string.nav_dashboard, Icons.Outlined.SpaceDashboard),
    Proxies("proxies", R.string.nav_proxies, Icons.Outlined.Public),
    Rules("rules", R.string.nav_rules, Icons.Outlined.VerifiedUser),
    Routing("routing", R.string.nav_routing, Icons.Outlined.Sync),
    Dns("dns", R.string.nav_dns, Icons.Outlined.CellTower),
    Evolution("evolution", R.string.nav_evolution, Icons.Outlined.Bolt),
    Logs("logs", R.string.nav_logs, Icons.Outlined.Description),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings),
    About("about", R.string.nav_about, Icons.Outlined.Info);

    companion object {
        val start: AppDestination = Dashboard

        fun fromRoute(route: String?): AppDestination =
            entries.firstOrNull { it.route == route } ?: start
    }
}
