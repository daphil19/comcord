package dev.phillipslabs.comcord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import dev.phillipslabs.comcord.bootstrap.createComcordAppBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureComcordLogging()
        val logger = comcordLogger("android.main")
        logger.info { "Starting Comcord Android target" }

        CoroutineScope(Dispatchers.Default).launch {
            val rootComponent =
                createComcordAppBootstrap {
                    MatrixMultiMessenger
                        .create(applicationContext) {
                            comcordMatrixConfig()
                        }.singleModeMatrixMessenger()
                        .first()
                }

            withContext(Dispatchers.Main) {
                setContent {
                    App(rootComponent)
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
