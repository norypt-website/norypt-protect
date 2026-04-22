package com.norypt.protect.dpm

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Controls visibility of the Norypt Protect launcher icon via activity-alias toggling.
 *
 * The actual MainActivity has no LAUNCHER intent-filter; the alias `.LauncherAlias`
 * carries it. Disabling the alias removes the icon from the app drawer.
 *
 * Note: icon visibility changes may take 1–2 minutes to propagate on some launchers.
 */
object LauncherAlias {

    private const val ALIAS_CLASS = "com.norypt.protect.LauncherAlias"

    private fun alias(ctx: Context): ComponentName = ComponentName(ctx.packageName, ALIAS_CLASS)

    fun isHidden(ctx: Context): Boolean {
        val state = ctx.packageManager.getComponentEnabledSetting(alias(ctx))
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    /**
     * Hides the launcher icon by disabling the alias component.
     * Works without Device Owner; any app can manage its own component state.
     */
    fun hide(ctx: Context) {
        ctx.packageManager.setComponentEnabledSetting(
            alias(ctx),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /**
     * Restores the launcher icon by re-enabling the alias component.
     */
    fun show(ctx: Context) {
        ctx.packageManager.setComponentEnabledSetting(
            alias(ctx),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
