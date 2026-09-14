package com.example.snapstoneprinter.data.print

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.printerTargetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "printer_target"
)

/**
 * Remembers which app the user picked the first time they shared a slip, so that every subsequent
 * print goes straight to it instead of re-opening the system chooser.
 *
 * Why this exists: printing a double-faced card fires TWO sequential [android.content.Intent#ACTION_SEND]
 * dispatches. Without a remembered target the user would have to pick their printer app twice per
 * card, which is exactly the kind of friction that makes a one-tap PRINT button pointless.
 *
 * The value is stored as a flattened `package/class` string rather than a serialised
 * [ComponentName] so it stays readable and survives a [ComponentName] API change. It is always
 * re-validated against [PackageManager] before use - an uninstalled or renamed target must fall
 * back to the chooser instead of throwing [android.content.ActivityNotFoundException].
 */
object PrinterTargetStore {

    private const val TAG = "PrinterTargetStore"

    private val KEY_COMPONENT = stringPreferencesKey("printer_component")
    private val KEY_LABEL = stringPreferencesKey("printer_label")

    /** The remembered target, or null when nothing has been chosen yet. */
    fun targetFlow(context: Context): Flow<PrinterTarget?> =
        context.applicationContext.printerTargetDataStore.data.map { prefs ->
            val flattened = prefs[KEY_COMPONENT] ?: return@map null
            val component = ComponentName.unflattenFromString(flattened) ?: return@map null
            PrinterTarget(component = component, label = prefs[KEY_LABEL] ?: component.packageName)
        }

    suspend fun remember(context: Context, component: ComponentName) {
        val label = resolveLabel(context, component)
        context.applicationContext.printerTargetDataStore.edit { prefs ->
            prefs[KEY_COMPONENT] = component.flattenToString()
            prefs[KEY_LABEL] = label
        }
        Log.i(TAG, "Remembered print target: ${component.flattenToShortString()} ($label)")
    }

    suspend fun forget(context: Context) {
        context.applicationContext.printerTargetDataStore.edit { prefs ->
            prefs.remove(KEY_COMPONENT)
            prefs.remove(KEY_LABEL)
        }
        Log.i(TAG, "Forgot the remembered print target")
    }

    /**
     * True when [component] can still handle an `image/png` [android.content.Intent#ACTION_SEND].
     * Uninstalling the printer app, or it renaming its share activity, must degrade to the chooser.
     */
    fun isResolvable(context: Context, component: ComponentName): Boolean = try {
        context.packageManager.getActivityInfo(component, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "Remembered target ${component.flattenToShortString()} is gone; using chooser", e)
        false
    }

    private fun resolveLabel(context: Context, component: ComponentName): String = try {
        val pm = context.packageManager
        pm.getActivityInfo(component, 0).loadLabel(pm).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        component.packageName
    }
}

/** A remembered share target plus its human-readable label for the "change target" UI. */
data class PrinterTarget(
    val component: ComponentName,
    val label: String
)
