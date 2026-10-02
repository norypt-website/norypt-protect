package com.norypt.protect.checkup

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.norypt.protect.admin.ProtectAdminReceiver
import com.norypt.protect.platform.PlatformInfo
import com.norypt.protect.prefs.ProtectPrefs
import java.time.LocalDate

sealed interface FixResult {
    data object Applied : FixResult
    data object Opened : FixResult
    data class Failed(val reason: String) : FixResult
}

/** Reads what the checkup judges, applies the fixes Norypt can make, and opens Settings for the rest. */
object CheckupReadings {

    /** Not a public constant, but readable by apps; when it cannot be read the item says so. */
    private const val PRIVATE_NOTIFICATIONS = "lock_screen_allow_private_notifications"
    private const val NEEDS_OWNER = "Requires Device Owner."
    private const val REFUSED = "Android refused the change."

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
    private fun admin(ctx: Context) = ComponentName(ctx, ProtectAdminReceiver::class.java)

    fun read(ctx: Context): Readings {
        val dpm = dpm(ctx)
        val owner = dpm?.isDeviceOwnerApp(ctx.packageName) == true
        val resolver = ctx.contentResolver
        return Readings(
            model = Build.MODEL,
            deviceName = runCatching { Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME) }.getOrNull(),
            bluetoothName = bluetoothName(ctx),
            bluetoothAllowed = bluetoothAllowed(ctx),
            adbEnabled = runCatching { Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrNull(),
            passwordComplexity = runCatching { dpm?.passwordComplexity }.getOrNull(),
            securityPatch = runCatching { LocalDate.parse(Build.VERSION.SECURITY_PATCH) }.getOrNull(),
            today = LocalDate.now(),
            alwaysOnVpn = if (owner) runCatching { dpm?.getAlwaysOnVpnPackage(admin(ctx)) }.getOrNull() else null,
            vpnReadable = owner,
            vpnLockdown = owner && runCatching { dpm?.isAlwaysOnVpnLockdownEnabled(admin(ctx)) == true }.getOrDefault(false),
            privateDnsMode = if (owner) runCatching { dpm?.getGlobalPrivateDnsMode(admin(ctx)) }.getOrNull() else null,
            keyguardDisabledFeatures = runCatching { dpm?.getKeyguardDisabledFeatures(admin(ctx)) ?: 0 }.getOrDefault(0),
            privateNotificationsAllowed = runCatching { Settings.Secure.getInt(resolver, PRIVATE_NOTIFICATIONS) != 0 }.getOrNull(),
            twoGBlocked = twoGBlocked(ctx),
            grapheneOs = PlatformInfo.isGrapheneOS(ctx),
            confirmed = ProtectPrefs.checkupConfirmed(ctx).mapNotNull { n -> CheckId.entries.firstOrNull { it.name == n } }.toSet(),
        )
    }

    /** What the item's button says when it is not OK. */
    fun fixLabel(id: CheckId, r: Readings): String = when (id) {
        CheckId.DEVICE_NAME -> "Use the model name"
        CheckId.BLUETOOTH_NAME -> if (r.bluetoothAllowed) "Use the model name" else "Allow Nearby devices"
        CheckId.LOCKSCREEN_NOTIFICATIONS -> "Hide content"
        CheckId.TRUST_AGENTS -> "Block Smart Lock"
        CheckId.CELLULAR_2G -> "Block 2G"
        else -> "Open Settings"
    }

    /** The runtime permission the owner must allow, with the system prompt, before [fix] can work. */
    fun permissionFor(id: CheckId, r: Readings): String? =
        if (id == CheckId.BLUETOOTH_NAME && !r.bluetoothAllowed) Manifest.permission.BLUETOOTH_CONNECT else null

    fun fix(ctx: Context, id: CheckId): FixResult = when (id) {
        CheckId.DEVICE_NAME -> setDeviceName(ctx)
        CheckId.BLUETOOTH_NAME -> setBluetoothName(ctx)
        CheckId.LOCKSCREEN_NOTIFICATIONS -> setKeyguardFlag(ctx, DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS, on = true)
        CheckId.TRUST_AGENTS -> setKeyguardFlag(ctx, DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS, on = true)
        CheckId.CELLULAR_2G -> block2G(ctx, block = true)
        else -> open(ctx, settingsAction(id))
    }

    /** Whether the item offers Undo: only for a policy that is in force. */
    fun canUndo(r: Readings, id: CheckId): Boolean = when (id) {
        CheckId.LOCKSCREEN_NOTIFICATIONS ->
            r.keyguardDisabledFeatures and DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS != 0
        CheckId.TRUST_AGENTS -> r.keyguardDisabledFeatures and DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS != 0
        CheckId.CELLULAR_2G -> r.twoGBlocked == true
        else -> false
    }

    fun undo(ctx: Context, id: CheckId): FixResult = when (id) {
        CheckId.LOCKSCREEN_NOTIFICATIONS -> setKeyguardFlag(ctx, DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS, on = false)
        CheckId.TRUST_AGENTS -> setKeyguardFlag(ctx, DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS, on = false)
        CheckId.CELLULAR_2G -> block2G(ctx, block = false)
        else -> FixResult.Failed("Nothing to undo.")
    }

    /** "I've set this" for a GrapheneOS item no app can read. */
    fun confirm(ctx: Context, id: CheckId) {
        ProtectPrefs.setCheckupConfirmed(ctx, ProtectPrefs.checkupConfirmed(ctx) + id.name)
    }

    private fun bluetoothAllowed(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked on the first line
    private fun bluetoothName(ctx: Context): String? {
        if (!bluetoothAllowed(ctx)) return null
        return runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.name }.getOrNull()
    }

    @SuppressLint("MissingPermission") // checked on the first line
    private fun setBluetoothName(ctx: Context): FixResult {
        if (!bluetoothAllowed(ctx)) return FixResult.Failed("Allow Nearby devices first.")
        val renamed = runCatching {
            ctx.getSystemService(BluetoothManager::class.java)?.adapter?.setName(Build.MODEL) == true
        }.getOrDefault(false)
        // Android renames only while Bluetooth is on; otherwise the owner renames it in Bluetooth settings.
        return if (renamed) FixResult.Applied else open(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    private fun setDeviceName(ctx: Context): FixResult {
        val written = runCatching {
            Settings.Global.putString(ctx.contentResolver, Settings.Global.DEVICE_NAME, Build.MODEL)
        }.getOrDefault(false)
        // Without WRITE_SECURE_SETTINGS (granted over ADB at provisioning) the owner renames it in About phone.
        return if (written) FixResult.Applied else open(ctx, Settings.ACTION_DEVICE_INFO_SETTINGS)
    }

    private fun setKeyguardFlag(ctx: Context, flag: Int, on: Boolean): FixResult {
        val dpm = dpm(ctx)
        if (dpm == null || !dpm.isDeviceOwnerApp(ctx.packageName)) return FixResult.Failed(NEEDS_OWNER)
        return runCatching<FixResult> {
            val current = dpm.getKeyguardDisabledFeatures(admin(ctx))
            dpm.setKeyguardDisabledFeatures(admin(ctx), if (on) current or flag else current and flag.inv())
            FixResult.Applied
        }.getOrElse { FixResult.Failed(REFUSED) }
    }

    private fun twoGBlocked(ctx: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return runCatching {
            ctx.getSystemService(UserManager::class.java)?.hasUserRestriction(UserManager.DISALLOW_CELLULAR_2G) == true
        }.getOrNull()
    }

    private fun block2G(ctx: Context, block: Boolean): FixResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return FixResult.Failed("Needs Android 14 or later.")
        val dpm = dpm(ctx)
        if (dpm == null || !dpm.isDeviceOwnerApp(ctx.packageName)) {
            return FixResult.Failed("$NEEDS_OWNER You can also turn off Allow 2G under Settings › Network & internet › SIMs.")
        }
        return runCatching<FixResult> {
            if (block) {
                dpm.addUserRestriction(admin(ctx), UserManager.DISALLOW_CELLULAR_2G)
            } else {
                dpm.clearUserRestriction(admin(ctx), UserManager.DISALLOW_CELLULAR_2G)
            }
            FixResult.Applied
        }.getOrElse { FixResult.Failed(REFUSED) }
    }

    private fun settingsAction(id: CheckId): String = when (id) {
        CheckId.USB_DEBUGGING -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
        CheckId.SCREEN_LOCK -> DevicePolicyManager.ACTION_SET_NEW_PASSWORD
        CheckId.SECURITY_PATCH -> "android.settings.SYSTEM_UPDATE_SETTINGS"
        CheckId.VPN -> Settings.ACTION_VPN_SETTINGS
        CheckId.PRIVATE_DNS -> Settings.ACTION_WIRELESS_SETTINGS
        else -> Settings.ACTION_SECURITY_SETTINGS
    }

    private fun open(ctx: Context, action: String): FixResult {
        if (start(ctx, action)) return FixResult.Opened
        // The system-update page is not public on every build; About phone always exists.
        return if (start(ctx, Settings.ACTION_DEVICE_INFO_SETTINGS)) FixResult.Opened else FixResult.Failed("Settings page not available.")
    }

    private fun start(ctx: Context, action: String): Boolean = runCatching {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
