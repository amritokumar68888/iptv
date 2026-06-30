package com.iptvplayer.app.data.repository

import com.iptvplayer.app.data.db.ChannelDao
import com.iptvplayer.app.data.db.PlaylistDao
import com.iptvplayer.app.data.model.Channel
import com.iptvplayer.app.data.model.Playlist
import com.iptvplayer.app.data.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val allPlaylists = playlistDao.getAllPlaylists()

    suspend fun addPlaylist(name: String, url: String, isLocal: Boolean = false): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val playlist = Playlist(name = name, url = url, isLocal = isLocal)
                val id = playlistDao.insertPlaylist(playlist)
                // Immediately load channels for this playlist
                loadChannels(id, url, isLocal)
                Result.success(id)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun refreshPlaylist(playlist: Playlist): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                loadChannels(playlist.id, playlist.url, playlist.isLocal)
                playlistDao.updatePlaylist(playlist.copy(lastUpdated = System.currentTimeMillis()))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        withContext(Dispatchers.IO) {
            channelDao.deleteChannelsByPlaylist(playlist.id)
            playlistDao.deletePlaylist(playlist)
        }
    }

    private suspend fun loadChannels(playlistId: Long, url: String, isLocal: Boolean) {
        val content = if (isLocal) {
            File(url).readText()
        } else {
            fetchRemoteContent(url)
        }
        val channels = M3uParser.parse(content, playlistId)
        channelDao.deleteChannelsByPlaylist(playlistId)
        channelDao.insertChannels(channels)
    }

    private fun fetchRemoteContent(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: throw Exception("Empty response")
        }
    }

    fun getChannelsByPlaylist(playlistId: Long) = channelDao.getChannelsByPlaylist(playlistId)
    fun getAllChannels() = channelDao.getAllChannels()
    fun getGroupsByPlaylist(playlistId: Long) = channelDao.getGroupsByPlaylist(playlistId)
    fun getAllGroups() = channelDao.getAllGroups()
    fun getChannelsByGroup(playlistId: Long, group: String) = channelDao.getChannelsByGroup(playlistId, group)
    fun getChannelsByGroupAll(group: String) = channelDao.getChannelsByGroupAll(group)
    fun getFavoriteChannels() = channelDao.getFavoriteChannels()
    fun searchChannels(query: String) = channelDao.searchChannels(query)

    suspend fun toggleFavorite(channel: Channel) {
        withContext(Dispatchers.IO) {
            channelDao.setFavorite(channel.id, !channel.isFavorite)
        }
    }
}
