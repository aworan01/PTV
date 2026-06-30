package com.personal.tv.ui

sealed class Screen(val route: String) {
    object TV        : Screen("tv")
    object Movies    : Screen("movies")
    object Shows     : Screen("shows")
    object Playlists : Screen("playlists")
    object Settings  : Screen("settings")
}
