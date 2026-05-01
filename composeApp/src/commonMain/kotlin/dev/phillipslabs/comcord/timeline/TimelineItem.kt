package dev.phillipslabs.comcord.timeline

import kotlinx.datetime.Instant

enum class TimelineEventKind {
    Message,
    Notice,
}

enum class MessageDeliveryStatus {
    Sending,
    Sent,
    Failed,
}

data class TimelineEventRecord(
    val id: String,
    val authorId: String?,
    val authorName: String,
    val body: String,
    val timestamp: Instant,
    val kind: TimelineEventKind,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.Sent,
    val retryKey: String? = null,
)

sealed interface TimelineItem {
    val key: String
}

data class TimelineDateDivider(
    override val key: String,
    val label: String,
) : TimelineItem

data class TimelineMessage(
    override val key: String,
    val authorName: String,
    val body: String,
    val timeLabel: String,
    val isOwn: Boolean,
    val groupedWithPrevious: Boolean,
    val deliveryStatus: MessageDeliveryStatus,
    val retryKey: String?,
) : TimelineItem

data class TimelineSystemNotice(
    override val key: String,
    val body: String,
) : TimelineItem
