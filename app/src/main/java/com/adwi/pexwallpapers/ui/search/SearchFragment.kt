package com.adwi.pexwallpapers.ui.search

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.adwi.pexwallpapers.R
import com.adwi.pexwallpapers.databinding.FragmentSearchBinding
import com.adwi.pexwallpapers.shared.adapter.ChipListAdapter
import com.adwi.pexwallpapers.shared.adapter.WallpaperListPagingAdapter
import com.adwi.pexwallpapers.shared.adapter.WallpapersLoadStateAdapter
import com.adwi.pexwallpapers.shared.base.BaseFragment
import com.adwi.pexwallpapers.shared.tools.SharingTools
import com.adwi.pexwallpapers.ui.preview.PreviewFragment
import com.adwi.pexwallpapers.util.*
import com.adwi.pexwallpapers.util.Constants.Companion.WALLPAPER_ID
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import timber.log.Timber

@AndroidEntryPoint
class SearchFragment :
    BaseFragment<FragmentSearchBinding, WallpaperListPagingAdapter>(
        FragmentSearchBinding::inflate,
        hasNavigation = true
    ) {
    override val viewModel: SearchViewModel by viewModels()

    private var _chipListAdapter: ChipListAdapter? = null
    private val chipListAdapter get() = _chipListAdapter

    private var lastQuery: String = ""

    override fun setupAdapters() {
        mAdapter = WallpaperListPagingAdapter(
            onItemClick = { wallpaper ->
                navigateToFragmentWithArgumentInt(WALLPAPER_ID, wallpaper.id, PreviewFragment())
            },
            onShareClick = { wallpaper ->
                wallpaper.url?.let {
                    SharingTools(requireContext())
                        .share(it)
                }
            },
            onFavoriteClick = { wallpaper ->
                viewModel.onFavoriteClick(wallpaper)
            },
            onPexelLogoClick = { wallpaper ->
                val uri = Uri.parse(wallpaper.url)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                requireActivity().startActivity(intent)
            },
            requireActivity = requireActivity(),
            buttonsVisible = false
        )

        _chipListAdapter = ChipListAdapter(
            onItemClick = { query ->
                viewModel.onSearchQuerySubmit(query)
                binding.chipsRecyclerView.fadeOut()
            }
        )
        _chipListAdapter!!.submitList(chipList.shuffled())
    }

    override fun setupViews() {
        setHasOptionsMenu(true)
//        showSuggestionsIfKeyboardActive()
        binding.apply {
            shimmerFrameLayout.visibility = View.GONE

            recyclerView.apply {
                adapter = mAdapter?.withLoadStateFooter(
                    WallpapersLoadStateAdapter(mAdapter!!::retry)
                )
                layoutManager =
                    StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                setHasFixedSize(true)
                itemAnimator?.changeDuration = 0
            }

            chipsRecyclerView.apply {
                adapter = chipListAdapter
                layoutManager =
                    StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.HORIZONTAL)
                setHasFixedSize(true)
                itemAnimator = null
                itemAnimator?.changeDuration = 0
            }
        }
    }

    override fun setupListeners() {
        binding.apply {
            swipeRefreshLayout.setOnRefreshListener {
                mAdapter?.refresh()
            }

            retryButton.setOnClickListener {
                mAdapter?.retry()
            }
        }
    }

    override fun setupFlows() {
        binding.apply {
            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                // collectLatest - as soon new data received, current block will be suspended
                viewModel.searchResults.collectLatest { data ->
                    mAdapter?.submitData(data)
                }
            }

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                viewModel.hasCurrentQuery.collect { hasCurrentQuery ->
                    instructionsTextview.isVisible = !hasCurrentQuery
                    swipeRefreshLayout.isEnabled = hasCurrentQuery
                    if (!hasCurrentQuery) {
                        recyclerView.isVisible = false
                    }
                }
            }

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                mAdapter?.loadStateFlow
                    ?.distinctUntilChangedBy { it.source.refresh }
                    ?.filter { it.source.refresh is LoadState.NotLoading }
                    ?.collect {
                        if (viewModel.pendingScrollToTopAfterRefresh && it.mediator?.refresh is LoadState.NotLoading) {
                            recyclerView.smoothScrollToPosition(0)
                            viewModel.pendingScrollToTopAfterRefresh = false
                            if (viewModel.pendingScrollToTopAfterNewQuery) {
                                recyclerView.smoothScrollToPosition(0)
                                viewModel.pendingScrollToTopAfterNewQuery = false
                            }
                            if (viewModel.pendingScrollToTopAfterNewQuery && it.mediator?.refresh is LoadState.NotLoading) {
                                recyclerView.smoothScrollToPosition(0)
                                viewModel.pendingScrollToTopAfterNewQuery = false
                            }
                        }
                    }
            }

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                mAdapter?.loadStateFlow
                    ?.collect { loadState ->
                        when (val refresh = loadState.mediator?.refresh) {
                            is LoadState.Loading -> {
                                shimmerFrameLayout.visibility = View.VISIBLE
                                shimmerFrameLayout.startShimmer()
                                errorTextview.isVisible = false
                                retryButton.isVisible = false
                                swipeRefreshLayout.isRefreshing = true
                                noResultsTextview.isVisible = false
                                recyclerView.isVisible = mAdapter!!.itemCount > 0

                                // there was a bug, that recyclerView was showing old data for a split second
                                // this is work round it
                                recyclerView.showIfOrVisible {
                                    !viewModel.newQueryInProgress && mAdapter!!.itemCount > 0
                                }
                                shimmerFrameLayout.showIfOrVisible {
                                    viewModel.newQueryInProgress
                                }

                                viewModel.refreshInProgress = true
                                viewModel.pendingScrollToTopAfterRefresh = true
                            }
                            is LoadState.NotLoading -> {
                                shimmerFrameLayout.stopShimmer()
                                shimmerFrameLayout.isVisible = false
                                errorTextview.isVisible = false
                                retryButton.isVisible = false
                                swipeRefreshLayout.isRefreshing = false
                                recyclerView.isVisible = mAdapter!!.itemCount > 0

                                val noResults =
                                    mAdapter!!.itemCount < 1 && loadState.append.endOfPaginationReached
                                            && loadState.source.append.endOfPaginationReached

                                noResultsTextview.isVisible = noResults

                                viewModel.refreshInProgress = false
                                viewModel.newQueryInProgress = false
                            }
                            is LoadState.Error -> {
                                shimmerFrameLayout.stopShimmer()
                                shimmerFrameLayout.isVisible = false
                                swipeRefreshLayout.isRefreshing = false
                                noResultsTextview.isVisible = false
                                recyclerView.isVisible = mAdapter!!.itemCount > 0

                                val noCachedResults =
                                    mAdapter!!.itemCount < 1 && loadState.source.append.endOfPaginationReached

                                errorTextview.isVisible = noCachedResults
                                retryButton.isVisible = noCachedResults

                                val errorMessage = getString(
                                    R.string.could_not_load_search_results,
                                    refresh.error.localizedMessage
                                        ?: getString(
                                            R.string.unknown_error_occurred
                                        )
                                )
                                errorTextview.text = errorMessage

                                if (viewModel.refreshInProgress) {
                                    showSnackbar(errorMessage)
                                }
                                viewModel.refreshInProgress = false
                                viewModel.pendingScrollToTopAfterRefresh = false
                                viewModel.newQueryInProgress = false
                            }
                        }
                    }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_search_wallpaper, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as SearchView

        binding.apply {
            searchItem.setOnMenuItemClickListener {
                chipsRecyclerView.isVisible = true
                true
            }
            searchView.onQueryTextSubmit { query ->
                viewModel.onSearchQuerySubmit(query)
                searchView.clearFocus()
                lastQuery = query
                chipsRecyclerView.fadeOut()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.action_refresh -> {
                mAdapter?.refresh()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onPause() {
        binding.shimmerFrameLayout.stopShimmer()
        super.onPause()
    }

    private fun showSuggestionsIfKeyboardActive() {
        var isKeyboardShowing = false

        binding.coordinatorLayout.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            binding.coordinatorLayout.getWindowVisibleDisplayFrame(r)
            val screenHeight = binding.coordinatorLayout.rootView.height

            // r.bottom is the position above soft keypad or device button.
            // if keypad is shown, the r.bottom is smaller than that before.
            // Source: https://stackoverflow.com/a/56616774/16082761
            val keypadHeight = screenHeight - r.bottom


            if (keypadHeight > screenHeight * 0.15) { // 0.15 ratio is perhaps enough to determine keypad height.
                // keyboard is opened
                if (!isKeyboardShowing) {
                    isKeyboardShowing = true
                    binding.chipsRecyclerView.fadeIn()
                    Timber.e("Fun - keyboard opened")
                }
            } else {
                // keyboard is closed
                if (isKeyboardShowing) {
                    isKeyboardShowing = false
                    binding.chipsRecyclerView.fadeOut()
                    Timber.e("Fun - keyboard closed")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _chipListAdapter = null
    }

    private val chipList = listOf(
        "Nature",
        "Food",
        "Photography",
        "Pretty",
        "Red",
        "Blue",
        "Orange",
        "Black",
        "Gray",
        "Christmas",
        "Animals",
        "Abstract",
        "White",
        "Sea",
        "Landscape",
        "Art",
        "Creative",
        "Yellow",
        "Purple",
        "Cars",
        "Poland",
        "House",
        "Flowers",
        "Red"
    )
}