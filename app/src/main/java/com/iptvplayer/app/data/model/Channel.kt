package com.iptvplayer.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val logoUrl: String = "",
    val group: String = "Uncategorized",
    val isFavorite: Boolean = false,
    val playlistId: Long = 0
)
