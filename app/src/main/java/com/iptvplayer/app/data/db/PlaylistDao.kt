package com.iptvplayer.app.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.iptvplayer.app.data.model.Playlist

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY name")
    fun getAllPlaylists(): LiveData<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY name")
    suspend fun getAllPlaylistsSync(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Update
    suspend fun updatePlaylist(playlist: Playlist)
}
