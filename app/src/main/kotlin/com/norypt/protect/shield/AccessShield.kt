package com.norypt.protect.shield

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.prefs.ProtectPrefs

/** The two channels stalkerware and keyloggers rely on. */
enum class ShieldKind { ACCESSIBILITY, KEYBOARD }

/**
 * Spyware shield: only system and approved outside accessibility services and keyboards can be
 * switched on (Device Owner policy). Nothing is turned off silently: Android refuses the policy
 * while an outside one is switched on and not approved, so the owner approves it or turns it off
 * in Settings first.
 */
object AccessShield {

    /** Outside packages switched on but not approved; the policy cannot apply while any remain. */
    internal fun unapproved(enabledOutside: Set<String>, approved: Set<String>): Set<String> = enabledOutside - approved

    /** Whether Android's permitted list (null = no restriction) differs from the owner's list. */
    internal fun needsReapply(chosen: Set<String>, platform: List<String>?): Boolean = chosen != platform?.toSet()

    fun isOn(ctx: Context, kind: ShieldKind): Boolean = ProtectPrefs.shieldOn(ctx, kind.name)

    fun approved(ctx: Context, kind: ShieldKind): Set<String> = ProtectPrefs.shieldAllowlist(ctx, kind.name)

    /** Whether Android applies the owner's list right now. */
    fun inForce(ctx: Context, kind: ShieldKind): Boolean = !needsReapply(approved(ctx, kind), permitted(ctx, kind))

    /** Every service or keyboard switched on, system ones included. */
    fun enabledAll(ctx: Context, kind: ShieldKind): Set<String> = when (kind) {
        ShieldKind.ACCESSIBILITY -> ctx.getSystemService(AccessibilityManager::class.java)
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
            .orEmpty().toSet()
        ShieldKind.KEYBOARD -> ctx.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            .orEmpty().toSet()
    }

    /** Outside (non-system) ones switched on: the ones the policy needs approved. */
    fun enabledOutside(ctx: Context, kind: ShieldKind): Set<String> =
        enabledAll(ctx, kind).filterNot { isSystem(ctx.packageManager, it) }.toSet()

    /** Turns the shield on with [approved] as the list. False, with nothing stored, when Android refuses. */
    fun enable(ctx: Context, kind: ShieldKind, approved: Set<String>): Boolean {
        if (!apply(ctx, kind, approved)) return false
        ProtectPrefs.setShieldAllowlist(ctx, kind.name, approved)
        ProtectPrefs.setShieldOn(ctx, kind.name, true)
        return true
    }

    /** Lifts the restriction completely; the approvals are kept for next time. */
    fun disable(ctx: Context, kind: ShieldKind): Boolean {
        if (!apply(ctx, kind, null)) return false
        ProtectPrefs.setShieldOn(ctx, kind.name, false)
        return true
    }

    /** Drops one approval. With the shield on, Android refuses while that package is switched on. */
    fun removeApproval(ctx: Context, kind: ShieldKind, pkg: String): Boolean {
        val remaining = approved(ctx, kind) - pkg
        if (isOn(ctx, kind) && !apply(ctx, kind, remaining)) return false
        ProtectPrefs.setShieldAllowlist(ctx, kind.name, remaining)
        return true
    }

    /** Service tick: puts the owner's list back where Android's differs. */
    fun reconcile(ctx: Context) {
        ShieldKind.entries.filter { isOn(ctx, it) && !inForce(ctx, it) }.forEach { apply(ctx, it, approved(ctx, it)) }
    }

    /** System as these policies count it: preinstalled, whether updated or not. */
    fun isSystem(pm: PackageManager, pkg: String): Boolean = runCatching {
        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0)).flags and ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

    private fun permitted(ctx: Context, kind: ShieldKind): List<String>? = runCatching {
        val dpm = dpm(ctx) ?: return null
        when (kind) {
            ShieldKind.ACCESSIBILITY -> dpm.getPermittedAccessibilityServices(admin(ctx))
            ShieldKind.KEYBOARD -> dpm.getPermittedInputMethods(admin(ctx))
        }
    }.getOrNull()

    private fun apply(ctx: Context, kind: ShieldKind, allow: Set<String>?): Boolean {
        val dpm = dpm(ctx) ?: return false
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return false
        val list = allow?.toList()
        return runCatching {
            when (kind) {
                ShieldKind.ACCESSIBILITY -> dpm.setPermittedAccessibilityServices(admin(ctx), list)
                ShieldKind.KEYBOARD -> dpm.setPermittedInputMethods(admin(ctx), list)
            }
        }.getOrDefault(false)
    }

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
    private fun admin(ctx: Context) = ComponentName(ctx, ProtectAdminReceiver::class.java)
}
