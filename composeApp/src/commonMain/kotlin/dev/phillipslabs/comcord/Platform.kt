package dev.phillipslabs.comcord

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform