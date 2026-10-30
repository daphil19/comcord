package dev.phillipslabs.comcord

import de.connect2x.trixnity.messenger.i18n.DefaultLanguages
import de.connect2x.trixnity.messenger.i18n.I18n
import de.connect2x.trixnity.messenger.i18n.Languages
import de.connect2x.trixnity.messenger.i18n.platformGetSystemLangModule
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessengerConfiguration
import de.connect2x.trixnity.messenger.platformMatrixMessengerSettingsHolderModule
import kotlinx.datetime.TimeZone
import org.koin.dsl.module

// TODO this is better than a function right?
// TODO we should probably make a function...
val comcordMatrixConfig: MatrixMultiMessengerConfiguration.() -> Unit = {
    appName = "Comcord"
    appId = "dev.phillipslabs.comcord"
    // TODO client engine eventually
//    httpClientEngine = CIO.create()

    messengerConfiguration {
        defaultHomeServer = "https://matrix.org"
    }

    // TODO this is modeled after the trxinity-messenger example
    // TODO having to set these ourselves is kinda annoying...
    modulesFactories +=
        listOf(
            ::platformMatrixMessengerSettingsHolderModule,
            ::platformGetSystemLangModule,
            {
                module {
                    single<Languages> { DefaultLanguages }
                    single<I18n> { object : I18n(get(), get(), get(), get<TimeZone>()) {} }
                }
            },
        )
}
