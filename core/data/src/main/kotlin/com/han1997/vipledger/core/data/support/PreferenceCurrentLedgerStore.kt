package com.han1997.vipledger.core.data.support

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.han1997.vipledger.core.domain.support.CurrentLedgerStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ledgerPreferences: DataStore<Preferences> by preferencesDataStore(name = "vip_ledger_prefs")

/** 「当前账本」用 DataStore 持久化，进程重启后仍停在上次使用的账本。 */
@Singleton
class PreferenceCurrentLedgerStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : CurrentLedgerStore {

    override fun observeLedgerId(): Flow<String?> =
        context.ledgerPreferences.data.map { preferences ->
            preferences[KEY_CURRENT_LEDGER_ID]?.takeIf { it.isNotBlank() }
        }

    override suspend fun setLedgerId(ledgerId: String) {
        context.ledgerPreferences.edit { preferences ->
            preferences[KEY_CURRENT_LEDGER_ID] = ledgerId
        }
    }

    private companion object {
        val KEY_CURRENT_LEDGER_ID = stringPreferencesKey("current_ledger_id")
    }
}