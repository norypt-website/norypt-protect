package com.norypt.protect.platform

import android.content.Context

/**
 * Runtime platform detection.
 *
 * GrapheneOS hides itself from stock build properties (fingerprint, release-keys,
 * brand all mimic stock Pixel) but reliably exposes a system feature flag so apps
 * that need to adapt to its hardening can opt in. We use the feature-flag path
 * instead of heuristics on build.id.
 */
object PlatformInfo {
    fun isGrapheneOS(ctx: Context): Boolean =
        ctx.packageManager.hasSystemFeature("grapheneos.version")
}
