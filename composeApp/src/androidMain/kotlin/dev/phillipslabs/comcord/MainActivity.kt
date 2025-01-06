package dev.phillipslabs.comcord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.connect2x.trixnity.messenger.create
import de.connect2x.trixnity.messenger.createRoot
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoroutineScope(Dispatchers.Default).launch {
            val rootViewModel =
                MatrixMultiMessenger
                    .create(applicationContext) {
                        comcordMatrixConfig()
                    }.singleModeMatrixMessenger()
                    .first()
                    .createRoot()

            withContext(Dispatchers.Main) {
                setContent {
                    App(rootViewModel)
                }
            }
        }
    }
}

// @Preview
// @Composable
// private fun AppAndroidPreview() {
//    App()
// }
