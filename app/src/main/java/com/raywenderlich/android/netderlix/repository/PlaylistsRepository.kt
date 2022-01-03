/*
 * Copyright (c) 2020 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * This project and source code may use libraries or frameworks that are
 * released under various Open-Source licenses. Use of those libraries and
 * frameworks are governed by their own individual licenses.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.android.netderlix.repository

import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.YouTubeRequestInitializer
import com.raywenderlich.android.netderlix.BuildConfig
import com.raywenderlich.android.netderlix.model.PlaylistPage
import com.raywenderlich.android.netderlix.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Repository that provides list of playlists for the Ray Wenderlich Youtube Channel. */
class PlaylistsRepository {

  private val youtubeService: YouTube by lazy {
    YouTube.Builder(
        AndroidHttp.newCompatibleTransport(),
        JacksonFactory.getDefaultInstance(),
        HttpRequestInitializer {  }
    ).setApplicationName(APP_NAME)
        .setYouTubeRequestInitializer(YouTubeRequestInitializer(BuildConfig.YOUTUBE_API_KEY))
        .build()
  }

  /** Returns paged list of Playlists available on the RW Channel. */
  suspend fun getPlaylists(pageToken: String? = null) = withContext(Dispatchers.IO) {
    val playlists= youtubeService
        .playlists()
        .list("contentDetails,id,localizations,player,snippet,status")
        .setChannelId(YOUTUBE_CHANNEL_ID)
        .setPageToken(pageToken)
        .execute()

    PlaylistPage(
        playlists.items?.map { playlist ->
          com.raywenderlich.android.netderlix.model.Playlist(
              playlist.id,
              playlist.snippet.title
          )
        } ?: listOf(),
        playlists.nextPageToken
    )
  }

  /** Returns list of videos in the given Playlist. */
  suspend fun getItems(playlist: com.raywenderlich.android.netderlix.model.Playlist) = withContext(Dispatchers.IO) {
    val playlistResponse = youtubeService.playlistItems()
        .list("contentDetails,id,snippet,status")
        .setPlaylistId(playlist.id)
        .execute()

    ArrayList(playlistResponse.items.mapNotNull { item ->
      val videos = youtubeService.videos()
          .list("contentDetails,id,liveStreamingDetails,localizations,player," +
              "recordingDetails,snippet,statistics,status,topicDetails")
          .setId(item.contentDetails.videoId)
          .execute()

      videos.items.firstOrNull()?.let { video ->
        Video(
            item.contentDetails.videoId,
            video.snippet.title,
            video.snippet.description,
            video.snippet.thumbnails.high.url,
            video.snippet.thumbnails.high.url,
            video.snippet.channelTitle
        )
      }
    })
  }

  companion object {
    private const val YOUTUBE_CHANNEL_ID = "UCz3cM4qLljXcQ8oWjMPgKZA"

    private const val APP_NAME = "Netderlix"
  }
}