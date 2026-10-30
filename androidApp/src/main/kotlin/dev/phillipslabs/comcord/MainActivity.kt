package dev.phillipslabs.comcord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create

class MainActivity : ComponentActivity() {
    private lateinit var applicationHost: ComcordApplicationHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applicationHost = ViewModelProvider(this)[ApplicationHostViewModel::class.java].host
        setContent {
            applicationHost.Content(
                factory = { MatrixMultiMessenger.create(applicationContext) { comcordMatrixConfig() } },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        applicationHost.resume()
    }

    override fun onStop() {
        applicationHost.stop()
        super.onStop()
    }
}

// Activity recreation retains the SDK lifetime and storage owner. Clearing the
// ViewModel closes it; removing an Account UI alone does not close all clients.
class ApplicationHostViewModel : ViewModel() {
    val host = ComcordApplicationHost()

    override fun onCleared() {
        host.destroy()
    }
}
