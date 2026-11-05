package dev.phillipslabs.comcord

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OpenMentionCallback
import org.koin.dsl.module
import java.util.concurrent.CopyOnWriteArrayList

/** Observes actual SDK contexts at its factory boundary; never substitutes timeline behavior. */
class TimelineProbe(
    messenger: MatrixMessenger,
) : TimelineViewModelFactory {
    data class Session(
        val context: MatrixClientViewModelContext,
        val vm: TimelineViewModel,
    )

    val sessions = CopyOnWriteArrayList<Session>()

    init {
        messenger.di.loadModules(listOf(module { single<TimelineViewModelFactory> { this@TimelineProbe } }))
    }

    override fun create(
        viewModelContext: MatrixClientViewModelContext,
        roomId: RoomId,
        onBack: () -> Unit,
        onOpenRoomSettings: () -> Unit,
        onOpenUserProfile: (UserId) -> Unit,
        onOpenMention: OpenMentionCallback,
        onOpenMetadata: (eventId: EventId) -> Unit,
    ): TimelineViewModel =
        TimelineViewModelFactory
            .create(
                viewModelContext,
                roomId,
                onBack,
                onOpenRoomSettings,
                onOpenUserProfile,
                onOpenMention,
                onOpenMetadata,
            ).also { sessions += Session(viewModelContext, it) }
}
