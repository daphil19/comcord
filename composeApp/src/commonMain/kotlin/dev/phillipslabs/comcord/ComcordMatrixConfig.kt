package dev.phillipslabs.comcord

import de.connect2x.trixnity.messenger.i18n.DefaultLanguages
import de.connect2x.trixnity.messenger.i18n.I18n
import de.connect2x.trixnity.messenger.i18n.Languages
import de.connect2x.trixnity.messenger.i18n.platformGetSystemLangModule
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessengerConfiguration
import de.connect2x.trixnity.messenger.platformMatrixMessengerSettingsHolderModule
import io.ktor.client.engine.HttpClientEngine
import kotlinx.datetime.TimeZone
import org.koin.dsl.module

private val matrixConfigLogger = comcordLogger("matrix.config")

data class ComcordCapabilityFlags(
    val media: Boolean = false,
    val notifications: Boolean = false,
    val encryptionUx: Boolean = false,
)

data class ComcordMatrixSettings(
    val appName: String = "Comcord",
    val appId: String = "dev.phillipslabs.comcord",
    val capabilityFlags: ComcordCapabilityFlags = ComcordCapabilityFlags(),
    val httpClientEngine: HttpClientEngine? = null,
)

fun MatrixMultiMessengerConfiguration.applyComcordMatrixConfig(settings: ComcordMatrixSettings = ComcordMatrixSettings()) {
    configureComcordLogging()

    appName = settings.appName
    appId = settings.appId
    httpClientEngine = settings.httpClientEngine

    modulesFactories +=
        listOf(
            ::platformMatrixMessengerSettingsHolderModule,
            ::platformGetSystemLangModule,
            {
                module {
                    single<Languages> { DefaultLanguages }
                    single<I18n> { object : I18n(get(), get(), get(), get<TimeZone>()) {} }
                    single { settings }
                    single { settings.capabilityFlags }
                }
            },
        )

    matrixConfigLogger.info { "Configured Matrix bootstrap for ${settings.appName}" }
}

val comcordMatrixConfig: MatrixMultiMessengerConfiguration.() -> Unit = {
    applyComcordMatrixConfig()
}
