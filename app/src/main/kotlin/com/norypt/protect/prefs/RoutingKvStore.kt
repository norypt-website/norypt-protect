package com.norypt.protect.prefs

/**
 * Splits the settings between two files: keys in [DeviceProtectedKeys] go to [deviceProtected],
 * which Android opens at boot, and all others to the encrypted file. The encrypted half is only
 * asked for when one of its own keys is used, so the triggers that run before the first unlock
 * after a restart never touch the Keystore or credential-encrypted storage. Any other key still
 * throws then, as it always did.
 */
internal class RoutingKvStore(
    private val deviceProtected: KvStore,
    private val encrypted: () -> KvStore,
    private val routed: (String) -> Boolean = DeviceProtectedKeys::routed,
) : KvStore {

    private fun target(key: String): KvStore = if (routed(key)) deviceProtected else encrypted()

    override fun getString(key: String, default: String?): String? = target(key).getString(key, default)
    override fun getInt(key: String, default: Int): Int = target(key).getInt(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = target(key).getBoolean(key, default)
    override fun getLong(key: String, default: Long): Long = target(key).getLong(key, default)
    override fun putString(key: String, value: String?) = target(key).putString(key, value)
    override fun putInt(key: String, value: Int) = target(key).putInt(key, value)
    override fun putBoolean(key: String, value: Boolean) = target(key).putBoolean(key, value)
    override fun putLong(key: String, value: Long) = target(key).putLong(key, value)
}

/**
 * Moves routed keys out of the encrypted file: the update from 1.3.1, where every setting lived
 * there. Pure, so the decision is unit-tested; ProtectPrefs applies it.
 */
internal object DeviceProtectedMigration {

    /**
     * [copy]: routed entries of the encrypted file that the device-protected file lacks.
     * [remove]: routed keys to delete from the encrypted file, those copied and those the
     * device-protected file already has (its value is newer: a wrong-PIN count typed before the
     * first unlock, say). A value of a type the settings never use stays where it is.
     */
    data class Plan(val copy: Map<String, Any>, val remove: Set<String>)

    fun plan(
        encrypted: Map<String, *>,
        deviceProtectedKeys: Set<String>,
        routed: (String) -> Boolean = DeviceProtectedKeys::routed,
    ): Plan {
        val copy = HashMap<String, Any>()
        val remove = HashSet<String>()
        for ((key, value) in encrypted) {
            if (!routed(key)) continue
            when {
                key in deviceProtectedKeys -> remove += key
                value != null && isSettingType(value) -> {
                    copy[key] = value
                    remove += key
                }
            }
        }
        return Plan(copy, remove)
    }

    private fun isSettingType(value: Any): Boolean =
        value is Boolean || value is Int || value is Long || value is String
}

/**
 * A lazy value that is kept only once [build] succeeds: opening the encrypted file before the
 * first unlock throws, and that failure must not stick once the phone is unlocked.
 */
internal class RetryingLazy<T : Any>(private val build: () -> T) {
    @Volatile private var value: T? = null

    fun get(): T {
        value?.let { return it }
        return synchronized(this) { value ?: build().also { value = it } }
    }
}
