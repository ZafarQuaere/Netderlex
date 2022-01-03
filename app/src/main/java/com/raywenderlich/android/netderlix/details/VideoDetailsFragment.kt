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

package com.raywenderlich.android.netderlix.details

import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.*
import com.raywenderlich.android.netderlix.R
import com.raywenderlich.android.netderlix.catalog.CatalogCardPresenter
import com.raywenderlich.android.netderlix.loadBitmap
import com.raywenderlich.android.netderlix.loadDrawable
import com.raywenderlich.android.netderlix.model.Video
import com.raywenderlich.android.netderlix.model.VideoItem
import com.raywenderlich.android.netderlix.playback.VideoPlaybackActivity
import com.raywenderlich.android.netderlix.showVideoDetails

/**
 * Fragment based on leanback details screens.
 * It shows a detailed view of video and its metadata plus related videos.
 */
class VideoDetailsFragment : DetailsSupportFragment() {

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)

    val videoItem = arguments?.getSerializable(ARGUMENT_VIDEO) as VideoItem

    adapter = ArrayObjectAdapter(createPresenterSelector(videoItem)).apply {
      add(createDetailsOverviewRow(videoItem.video, this))
      add(createRelatedVideosRow(videoItem))
    }

    onItemViewClickedListener = OnItemViewClickedListener { itemViewHolder, item, _, _ ->
      if(item is VideoItem) {
        showVideoDetails(requireActivity(), itemViewHolder, item)
      }
    }

    initializeBackground(videoItem.video)
  }

  private fun initializeBackground(video: Video?) {
    val backgroundController = DetailsSupportFragmentBackgroundController(this)

    backgroundController.enableParallax()

    loadBitmap(requireActivity(), video?.backgroundImageUrl, R.drawable.default_background) { bitmap ->
      backgroundController.coverBitmap = bitmap
      adapter.notifyItemRangeChanged(0, adapter.size())
    }
  }

  private fun createPresenterSelector(videoItem: VideoItem) = ClassPresenterSelector().apply {
      addClassPresenter(
          DetailsOverviewRow::class.java,
          createDetailsOverviewRowPresenter(videoItem, ::onActionClicked)
      )

      addClassPresenter(
          ListRow::class.java,
          ListRowPresenter()
      )
    }


  private fun createDetailsOverviewRow(selectedVideo: Video, detailsAdapter: ArrayObjectAdapter):
      DetailsOverviewRow {
    val context = requireContext()

    val row = DetailsOverviewRow(selectedVideo).apply {
      imageDrawable = ContextCompat.getDrawable(context, R.drawable.default_background)
      actionsAdapter = getActionAdapter()
    }

    val width = resources.getDimensionPixelSize(R.dimen.details_thumbnail_width)
    val height = resources.getDimensionPixelSize(R.dimen.details_thumbnail_height)

    loadDrawable(requireActivity(), selectedVideo.cardImageUrl, R.drawable.default_background, width,
        height)
    { resource ->
      row.imageDrawable = resource
      detailsAdapter.notifyArrayItemRangeChanged(0, detailsAdapter.size())
    }

    return row
  }

  private fun getActionAdapter() = ArrayObjectAdapter().apply {
    add(
        Action(
            ACTION_WATCH,
            resources.getString(R.string.watch_action_title),
            resources.getString(R.string.watch_action_subtitle)
        )
    )
  }

  private fun createDetailsOverviewRowPresenter(videoItem: VideoItem,
      actionHandler: (Action, VideoItem) -> Unit): FullWidthDetailsOverviewRowPresenter {
    return FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter()).apply {
      // Set detail background.
      backgroundColor =
          ContextCompat.getColor(requireContext(), R.color.selected_background)

      // Hook up transition element.
      val sharedElementHelper = FullWidthDetailsOverviewSharedElementHelper()
      sharedElementHelper.setSharedElementEnterTransition(
          activity, VideoDetailsActivity.SHARED_ELEMENT_NAME)
      setListener(sharedElementHelper)
      isParticipatingEntranceTransition = true

      onActionClickedListener = OnActionClickedListener { actionHandler(it, videoItem) }
    }
  }

  private fun createRelatedVideosRow(videoItem: VideoItem): ListRow {
    val selectedVideo = videoItem.video
    val playlistVideos = videoItem.playlist

    // Use other playlist videos as candidates for recommendations.
    val recommendations = ArrayList(playlistVideos.filterNot { it == selectedVideo })

    val listRowAdapter = ArrayObjectAdapter(CatalogCardPresenter())
    val header = HeaderItem(0, getString(R.string.related_videos))

    if (recommendations.isNotEmpty()) {
      for (i in 0 until NUM_RECOMMENDATIONS) {
        listRowAdapter.add(
            VideoItem(recommendations.random(), playlistVideos)
        )
      }
    }

    return ListRow(header, listRowAdapter)
  }

  private fun onActionClicked(action: Action, videoItem: VideoItem) {
    if (action.id == ACTION_WATCH) {
      val intent = VideoPlaybackActivity.newIntent(requireContext(), videoItem)
      startActivity(intent)
    }
  }

  companion object {

    private const val ACTION_WATCH = 1L

    private const val NUM_RECOMMENDATIONS = 10

    private const val ARGUMENT_VIDEO = "video"

    /** Creates new instance of this fragment that shows details for the given [Video]. */
    fun newInstance(video: VideoItem) = VideoDetailsFragment().apply {
      arguments = bundleOf(
          ARGUMENT_VIDEO to video
      )
    }
  }
}