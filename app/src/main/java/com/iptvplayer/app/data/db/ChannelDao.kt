package com.iptvplayer.app.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.iptvplayer.app.data.model.Channel

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY `group`, name")
    fun getChannelsByPlaylist(playlistId: Long): LiveData<List<Channel>>

    @Query("SELECT * FROM channels ORDER BY `group`, name")
    fun getAllChannels(): LiveData<List<Channel>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY `group`, name")
    suspend fun getChannelsByPlaylistSync(playlistId: Long): List<Channel>

    @Query("SELECT DISTINCT `group` FROM channels WHERE playlistId = :playlistId ORDER BY `group`")
    fun getGroupsByPlaylist(playlistId: Long): LiveData<List<String>>

    @Query("SELECT DISTINCT `group` FROM channels ORDER BY `group`")
    fun getAllGroups(): LiveData<List<String>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND `group` = :group ORDER BY name")
    fun getChannelsByGroup(playlistId: Long, group: String): LiveData<List<Channel>>

    @Query("SELECT * FROM channels WHERE `group` = :group ORDER BY name")
    fun getChannelsByGroupAll(group: String): LiveData<List<Channel>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY name")
    fun getFavoriteChannels(): LiveData<List<Channel>>

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' OR `group` LIKE '%' || :query || '%'")
    fun searchChannels(query: String): LiveData<List<Channel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)

    @Update
    suspend fun updateChannel(channel: Channel)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Long)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun setFavorite(channelId: Long, isFavorite: Boolean)
}
