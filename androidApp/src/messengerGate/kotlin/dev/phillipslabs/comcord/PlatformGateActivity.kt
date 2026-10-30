package dev.phillipslabs.comcord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create

class PlatformGateActivity : ComponentActivity() {
    private val gate = PlatformGateHost()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            gate.Content(
                factory = { MatrixMultiMessenger.create(applicationContext) { platformGateConfiguration() } },
                onClose = { finish() },
            )
        }
    }

    override fun onResume() { super.onResume(); gate.resume() }
    override fun onStop() { gate.stop(); super.onStop() }
    override fun onDestroy() { gate.destroy(); super.onDestroy() }
}
