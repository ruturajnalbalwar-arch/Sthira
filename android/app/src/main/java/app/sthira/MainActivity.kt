package app.sthira

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.sthira.nav.SthiraApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Patient video is on screen: block screenshots, screen recording and the recents thumbnail.
        // Only debug builds (never shared) leave capture on, for UI review.
        if (!BuildConfig.ALLOW_SCREEN_CAPTURE) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        setContent { SthiraApp() }
    }
}
