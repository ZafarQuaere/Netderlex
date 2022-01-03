/*
 * Copyright (c) 2021 Razeware LLC
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

package com.raywenderlich.android.netderlix.catalog

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.raywenderlich.android.netderlix.R
import com.raywenderlich.android.netderlix.details.VideoDetailsActivity
import com.raywenderlich.android.netderlix.error.ErrorFragment
import com.raywenderlich.android.netderlix.loadDrawable
import com.raywenderlich.android.netderlix.model.Playlist
import com.raywenderlich.android.netderlix.model.PlaylistPage
import com.raywenderlich.android.netderlix.model.Video
import com.raywenderlich.android.netderlix.model.VideoItem
import com.raywenderlich.android.netderlix.repository.PlaylistsRepository
import com.raywenderlich.android.netderlix.showVideoDetails
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Loads a grid of cards with videos to browse.
 */
class CatalogFragment : BrowseSupportFragment() {

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)

    setupTitleAndHeaders()
    loadAndShowPlaylists()

    onItemViewClickedListener = OnItemViewClickedListener { itemViewHolder, item, _, _ ->
      if(item is VideoItem) {
        showVideoDetails(requireActivity(), itemViewHolder, item)
      }
    }

    initializeBackground()
  }

  private fun initializeBackground() {
    val backgroundManager = BackgroundManager.getInstance(activity).apply {
      attach(activity?.window)
    }

    val metrics = DisplayMetrics()
    requireActivity().windowManager.defaultDisplay.getMetrics(metrics)

    onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
      if (item is VideoItem) {
        viewLifecycleOwner.lifecycleScope.launch {
          delay(BACKGROUND_UPDATE_DELAY)

          loadDrawable(
              requireActivity(),
              item.video.backgroundImageUrl,
              R.drawable.default_background,
              metrics.widthPixels,
              metrics.heightPixels
          ) {
            backgroundManager.drawable = it
          }
        }
      }
    }
  }

  private fun loadAndShowPlaylists() {
    val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    val cardPresenter = CatalogCardPresenter()

    val onPlaylistLoaded = { playlist: Playlist,
        videos: ArrayList<Video>,
        lastPlaylistInBatch: Boolean ->
      val listRowAdapter = ArrayObjectAdapter(cardPresenter)

      videos.forEach { video ->
        listRowAdapter.add(VideoItem(video, videos))
      }

      val header = HeaderItem(rowsAdapter.size().toLong(), playlist.title)
      rowsAdapter.add(ListRow(header, listRowAdapter))

      if (lastPlaylistInBatch) {
        progressBarManager.hide()
      }
    }

    progressBarManager.show()
    loadPlaylists(onPlaylistLoaded, ::showError)

    adapter = rowsAdapter
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    when (requestCode) {
      REQUEST_GOOGLE_PLAY_SERVICES -> if (resultCode != FragmentActivity.RESULT_OK) {
        showError(getString(R.string.error_play_services_missing))
      } else {
        loadAndShowPlaylists()
      }

      REQUEST_AUTHORIZATION -> if (resultCode == FragmentActivity.RESULT_OK) {
        loadAndShowPlaylists()
      }
    }
  }

  /** Helper function that shows fullscreen error message. */
  private fun showError(message: String) {
    // Make sure progress bar is hidden
    progressBarManager.hide()

    requireActivity().supportFragmentManager
        .beginTransaction()
        .add(
            R.id.main_frame,
            ErrorFragment.newInstance(
                getString(R.string.app_name),
                message
            )
        )
        .commit()
  }

  private fun setupTitleAndHeaders() {
    title = getString(R.string.browse_title)

    // over title
    headersState = HEADERS_ENABLED
    isHeadersTransitionOnBackEnabled = true

    // set fastLane (or headers) background color
    brandColor = ContextCompat.getColor(requireContext(), R.color.fastlane_background)
  }

  private fun loadPlaylists(
      onPlaylistLoaded: (Playlist, ArrayList<Video>, Boolean) -> Unit,
      onError: (String) -> Unit) {

    if (!isGooglePlayServicesAvailable()) {
      acquireGooglePlayServices()
    } else {
      viewLifecycleOwner.lifecycleScope.launch {
        val repository = PlaylistsRepository()
        var playlistsPage: PlaylistPage? = null

        do {
          try {
            playlistsPage = repository.getPlaylists(playlistsPage?.nextPageToken)

            playlistsPage.playlists.forEachIndexed { index, playlist ->
              val items = repository.getItems(playlist)
              onPlaylistLoaded(playlist, items, index == playlistsPage.playlists.size - 1)
            }
          } catch (_: IOException) {
            onError(getString(R.string.error_youtube_service))
            break
          }
        } while (playlistsPage?.nextPageToken != null)
      }
    }
  }

  private fun isGooglePlayServicesAvailable(): Boolean {
    val apiAvailability = GoogleApiAvailability.getInstance() ?: return false

    val connectionStaticCode = apiAvailability.isGooglePlayServicesAvailable(requireContext())
    return connectionStaticCode == ConnectionResult.SUCCESS
  }

  private fun acquireGooglePlayServices() {
    val apiAvailability = GoogleApiAvailability.getInstance() ?: return

    val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(requireContext())

    if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
      progressBarManager.hide()
      showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode)
    }
  }

  private fun showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode: Int) {
    val apiAvailability = GoogleApiAvailability.getInstance() ?: return

    val dialog = apiAvailability.getErrorDialog(
        requireActivity(),
        connectionStatusCode,
        REQUEST_GOOGLE_PLAY_SERVICES
    )

    dialog.show()
  }

  companion object {

    private const val BACKGROUND_UPDATE_DELAY = 300L

    const val REQUEST_AUTHORIZATION = 1001
    const val REQUEST_GOOGLE_PLAY_SERVICES = 1002
  }
}