package dev.phillipslabs.comcord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import de.connect2x.trixnity.messenger.createRoot
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val matrixMessenger = initializeMessenger()
            val rootViewModel = matrixMessenger.createRoot()
            setContent {
                App(rootViewModel, matrixMessenger)
            }
        }
    }

    private suspend fun initializeMessenger(dispatcher: CoroutineDispatcher = Dispatchers.Default) =
        withContext(dispatcher) {
            MatrixMultiMessenger
                .create(applicationContext) {
                    comcordMatrixConfig()
                }.singleModeMatrixMessenger()
                .first()
        }
}

// @Preview
// @Composable
// private fun AppAndroidPreview() {
//    App()
// }
