package dev.snapstonewielder.app.data.print

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Captures what the user actually picked in the system share sheet.
 *
 * [Intent.createChooser] with an `IntentSender` is the only supported way to learn the chosen
 * target: the system broadcasts back to us with [Intent.EXTRA_CHOSEN_COMPONENT] once the user
 * commits to an app. `startActivityForResult` on the chooser does NOT tell you who was picked.
 *
 * The [android.app.PendingIntent] wrapping this receiver must be `FLAG_MUTABLE`, because the
 * system is the one that fills in the chosen-component extra.
 */
class ChosenComponentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val component = intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        if (component == null) {
            Log.w(TAG, "Chooser callback carried no EXTRA_CHOSEN_COMPONENT")
            return
        }
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        scope.launch {
            try {
                PrinterTargetStore.remember(appContext, component)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist the chosen print target", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ChosenComponentRx"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
