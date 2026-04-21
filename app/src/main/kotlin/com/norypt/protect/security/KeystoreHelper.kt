package com.norypt.protect.security

import android.content.Context
import androidx.security.crypto.MasterKey

object KeystoreHelper {
    fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
}
