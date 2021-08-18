package com.adwi.pexwallpapers.ui.search

import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.adwi.pexwallpapers.R
import com.adwi.pexwallpapers.databinding.FragmentSearchBinding
import com.adwi.pexwallpapers.shared.adapter.ChipListAdapter
import com.adwi.pexwallpapers.shared.adapter.WallpaperListPagingAdapter
import com.adwi.pexwallpapers.shared.adapter.WallpapersLoadStateAdapter
import com.adwi.pexwallpapers.shared.base.BaseFragment
import com.adwi.pexwallpapers.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter


@AndroidEntryPoint
class SearchFragment :
    BaseFragment<FragmentSearchBinding, WallpaperListPagingAdapter>(
        FragmentSearchBinding::inflate,
        hasNavigation = true
    ) {
    override val viewModel: SearchViewModel by viewModels()

    private var _chipListAdapter: ChipListAdapter? = null
    private val chipListAdapter get() = _chipListAdapter

    override fun setupToolbar() {

        binding.apply {
            toolbarLayout.apply {
//                searchView.onQueryTextSubmit { query ->
//                    newQuery(query)
//                }
                searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        chipsRecyclerView.fadeOut()
                        tintView.fadeOut()
                        bottomNav.isVisible = true
                        swipeRefreshLayout.isClickable = true
                        backButton.backButtonLayout.visibility = View.GONE


                    } else {
                        chipsRecyclerView.fadeIn()
                        tintView.fadeIn()
                        bottomNav.isVisible = false
                        swipeRefreshLayout.isClickable = false
                        backButton.apply {
                            backButtonLayout.visibility = View.VISIBLE
                            backImageView.setOnClickListener {
                                searchView.clearFocus()
                                searchView.setQuery("", false)
                            }
                        }
                    }
                }
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        newQuery(query!!)
                        return true
                    }

                    override fun onQueryTextChange(query: String?): Boolean {
                        val filteredModelList = ArrayList<String>()
                        query?.let {
                            suggestionList.forEachIndexed { index, suggestion ->
                                if (suggestion.contains(query, true)) {
                                    filteredModelList.add(suggestion)
                                    if (filteredModelList.isNotEmpty()) {
                                        chipListAdapter?.submitList(filteredModelList)
                                    }
                                }
                            }
                        }
                        return true
                    }
                })
            }
        }
    }

    override fun setupAdapters() {
        mAdapter = WallpaperListPagingAdapter(
            requireActivity = requireActivity(),
            onItemClick = { wallpaper ->
                findNavController().navigate(
                    SearchFragmentDirections.actionSearchFragmentToPreviewFragment(
                        wallpaper
                    )
                )
            }
        )
        _chipListAdapter = ChipListAdapter(
            onItemClick = { query ->
                newQuery(query)
            }
        )
        _chipListAdapter!!.submitList(suggestionList.shuffled())
    }

    override fun setupViews() {
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
            toolbarLayout.menuButton.apply {
                menuImageView.setOnClickListener {
                    showMenu(menuImageView, R.menu.menu_search_wallpaper)
                }
            }
        }
    }

    override fun setupFlows() {
        binding.apply {
            launchCoroutine {
                // collectLatest - as soon new data received, current block will be suspended
                viewModel.searchResults.collectLatest { data ->
                    mAdapter?.submitData(data)
                }
            }
            launchCoroutine {
                viewModel.hasCurrentQuery.collect { hasCurrentQuery ->
                    instructionsTextview.isVisible = !hasCurrentQuery
                    swipeRefreshLayout.isEnabled = hasCurrentQuery
                    if (!hasCurrentQuery) {
                        recyclerView.isVisible = false
                    }
                }
            }
            launchCoroutine {
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
            launchCoroutine {
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

    private fun newQuery(query: String) {
        viewModel.onSearchQuerySubmit(query)
        binding.apply {
            chipsRecyclerView.fadeOut()
            toolbarLayout.searchView.clearFocus()
        }
        hideKeyboard()
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.action_refresh -> {
                mAdapter?.refresh()
                true
            }
            else -> false
        }
    }

    override fun onPause() {
        binding.shimmerFrameLayout.stopShimmer()
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _chipListAdapter = null
    }

    private val suggestionList by lazy {
        listOf(
            getString(R.string.nature),
            getString(R.string.food),
            getString(R.string.photography),
            getString(R.string.pretty),
            getString(R.string.red),
            getString(R.string.blue),
            getString(R.string.orange),
            getString(R.string.black),
            getString(R.string.gray),
            getString(R.string.christmas),
            getString(R.string.animals),
            getString(R.string.abstr),
            getString(R.string.white),
            getString(R.string.sea),
            getString(R.string.landscape),
            getString(R.string.art),
            getString(R.string.creative),
            getString(R.string.yellow),
            getString(R.string.purple),
            getString(R.string.cars),
            getString(R.string.horses),
            getString(R.string.dogs),
            getString(R.string.cats),
            getString(R.string.beach),
            getString(R.string.butterfly),
            getString(R.string.flowers),
            getString(R.string.racing)
        )
    }
}