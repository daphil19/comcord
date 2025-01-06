package dev.phillipslabs.comcord

import androidx.compose.material.Text
import androidx.compose.runtime.*
import com.arkivanov.decompose.extensions.compose.stack.Children
import de.connect2x.trixnity.messenger.viewmodel.RootRouter
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

// @Suppress("ktlint:compose:preview-public-check")
// @Composable
// @Preview
// fun App() {
//    MaterialTheme {
//        var showContent by remember { mutableStateOf(false) }
//        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
//            Button(onClick = { showContent = !showContent }) {
//                Text("Click me!")
//            }
//            AnimatedVisibility(showContent) {
//                val greeting = remember { Greeting().greet() }
//                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
//                    Image(painterResource(Res.drawable.compose_multiplatform), null)
//                    Text("Compose: $greeting")
//                }
//            }
//        }
//    }
// }

// suspend fun create() {
//    MatrixMessenger.create()
// }

@Suppress("ktlint:compose:preview-public-check")
@Composable
@Preview
fun App(rootViewModel: RootViewModel) {
    Children(stack = rootViewModel.stack) {
        when (val child = it.instance) {
            is RootRouter.Wrapper.AddMatrixAccount -> ImplementMeView(child)
            is RootRouter.Wrapper.Main -> ImplementMeView(child)
            is RootRouter.Wrapper.MatrixClientInitialization -> ImplementMeView(child)
            is RootRouter.Wrapper.MatrixClientLogout -> ImplementMeView(child)
            RootRouter.Wrapper.None -> ImplementMeView(child)
            is RootRouter.Wrapper.PasswordLogin -> ImplementMeView(child)
            is RootRouter.Wrapper.RegisterNewAccount -> ImplementMeView(child)
            is RootRouter.Wrapper.SSOLogin -> ImplementMeView(child)
            is RootRouter.Wrapper.StoreFailure -> ImplementMeView(child)
        }
    }
}

// Helper view for seeing needed implementations without throwing runtime errors like TODO does
@Composable
private fun ImplementMeView(child: RootRouter.Wrapper) {
    Text("The state ${child::class.qualifiedName} needs to be implemented.")
}
