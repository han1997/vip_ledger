package com.han1997.vipledger.core.data.support

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.han1997.vipledger.core.domain.support.LastAccountStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PreferenceLastAccountStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : LastAccountStore {
    override suspend fun getLastAccountId(ledgerId: String): String? =
        context.ledgerPreferences.data.first()[keyFor(ledgerId)]?.takeIf(String::isNotBlank)

    override suspend fun setLastAccountId(ledgerId: String, accountId: String) {
        context.ledgerPreferences.edit { preferences -> preferences[keyFor(ledgerId)] = accountId }
    }

    private fun keyFor(ledgerId: String) = stringPreferencesKey("last_account_$ledgerId")
}
