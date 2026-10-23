package dev.phillipslabs.comcord

/** Child links alone define membership. Each walk owns its visited set, so cycles terminate. */
internal fun MatrixDirectory.projectDirectory(): RoomDirectoryState {
    val spacesById = spaces.associateBy { it.id }
    val channelsById = channels.associateBy { it.id }
    val nestedSpaceIds = spaces.flatMap { it.children }.map { it.id }.toSet()
    val servers = spaces.filter { it.id !in nestedSpaceIds }.sortedBy { it.name }
    val uncategorized = mutableMapOf(ALL_ROOMS_VIEW_ID to channels.sortedBy { it.name })
    val categories = mutableMapOf<String, List<CategoryUi>>()
    for (server in servers) {
        val directChannels = server.children.orderedChannels(channelsById)
        uncategorized[server.id] = directChannels
        val included = directChannels.mapTo(mutableSetOf()) { it.id }
        categories[server.id] =
            server.children
                .sortedWith(childOrder { spacesById[it]?.name })
                .mapNotNull { link ->
                    val category = spacesById[link.id] ?: return@mapNotNull null
                    val descendants = mutableListOf<SpaceChild>()
                    val visited = mutableSetOf(server.id)
                    val pending = ArrayDeque<String>()
                    pending.add(category.id)
                    while (pending.isNotEmpty()) {
                        val id = pending.removeFirst()
                        if (visited.add(id)) {
                            val children = spacesById[id]?.children.orEmpty()
                            descendants.addAll(children.filter { it.id in channelsById })
                            pending.addAll(children.map { it.id }.filter { it in spacesById })
                        }
                    }
                    CategoryUi(
                        category.id,
                        category.name,
                        descendants.orderedChannels(channelsById).filter { included.add(it.id) },
                    )
                }
    }
    return RoomDirectoryState(
        servers = servers.map { ServerUi(it.id, it.name) },
        channelsByServer = uncategorized,
        syncStateLabel = syncStateLabel,
        categoriesByServer = categories,
    )
}

private const val MAX_CHILD_ORDER_LENGTH = 50

private fun SpaceChild.validOrder(): String? =
    order?.takeIf { value ->
        value.length <= MAX_CHILD_ORDER_LENGTH && value.all { it in ' '..'~' }
    }

private fun List<SpaceChild>.orderedChannels(channels: Map<String, ChannelUi>): List<ChannelUi> =
    sortedWith(childOrder { channels[it]?.name })
        .mapNotNull { channels[it.id] }
        .distinctBy { it.id }

private fun childOrder(name: (String) -> String?): Comparator<SpaceChild> =
    compareBy<SpaceChild> { it.validOrder() == null }
        .thenBy { it.validOrder() }
        .thenBy { name(it.id) }
        .thenBy { it.id }
