package app.sthira

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.test.runner.AndroidJUnitRunner
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/**
 * Keeps the screen on for every Sthira activity a test opens, from its creation (a flag on
 * Sthira's own window only; no device setting is touched), and refuses to run on a locked phone
 * instead of failing every test with "no compose hierarchies".
 */
class SthiraTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val keyguard = targetContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        check(!keyguard.isKeyguardLocked) { "The phone is locked: unlock it and keep the screen on before running the tests." }
    }

    override fun onStart() {
        runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback { activity, stage ->
                if (stage == Stage.PRE_ON_CREATE) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        super.onStart()
    }
}
