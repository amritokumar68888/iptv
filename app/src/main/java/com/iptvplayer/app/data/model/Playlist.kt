package com.iptvplayer.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,          // remote M3U URL or local file path
    val isLocal: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
