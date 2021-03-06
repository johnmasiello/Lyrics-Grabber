package com.example.john.lyricsbuddy;

import android.arch.core.util.Function;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

import static com.example.john.lyricsbuddy.LyricDatabaseHelper.SongLyrics;
import static com.example.john.lyricsbuddy.LyricDatabaseHelper.SongLyricsDao;
import static com.example.john.lyricsbuddy.LyricDatabaseHelper.SongLyricsListItem;

/**
 * Created by john on 4/14/18.
 * ViewModel that holds LiveData that fetches song lyric list items using Room
 * This ViewModel works in tandem with SongLyricDetailItemViewModel; as such, they
 * should be given the context of the same activity
 */

public class SongLyricsListViewModel extends ViewModel {
    private SongLyricsDao mSongLyricsDao;
    private MediatorLiveData<List<SongLyricsListItem>> mSongLyricListItems;
    private LiveData<List<SongLyricsListItem>> mFilteredSongLyricListItems;
    private MutableLiveData<List<SongLyricsListItem>> mFilterSongLyricListItemsWorker;
    private int mSortOrder;
    public String searchQuery;
    public int filterId;

    /**
     * Allows this view model to listen to changes in liveData in the detail view model
     */
    private WeakReference<SongLyricDetailItemViewModel> mSongLyricViewModel;
    /**
     * Flag to indicate LiveData&lt;SongLyric> has been set as source {@link #mSongLyricListItems}
     */
    private boolean songLyricsSourcedToListItems;

    public static final int ORDER_RECENT    = 0;
    public static final int ORDER_ARTIST    = 1;
    public static final int ORDER_ALBUM     = 2;
    public static final int ORDER_TRACK     = 3;

    public SongLyricsListViewModel() {
        mSortOrder = ORDER_RECENT;
        songLyricsSourcedToListItems = false;
        filterId = R.id.menu_filter_any;

        mFilterSongLyricListItemsWorker = new MutableLiveData<>();
    }

    public LiveData<List<SongLyricsListItem>> getLyricList() {
        return getLyricList(false);
    }

    public LiveData<List<SongLyricsListItem>> getLyricList(boolean forceRefresh) {
        return getLyricList(mSortOrder, forceRefresh);
    }

    public LiveData<List<SongLyricsListItem>> getLyricList(int sortOrder, boolean forceRefresh) {
        boolean needsToFetchItems = false;

        if (mSongLyricListItems == null) {
            mSongLyricListItems = new MediatorLiveData<>();
            mFilteredSongLyricListItems = Transformations.switchMap(mSongLyricListItems,
                    new Function<List<SongLyricsListItem>, LiveData<List<SongLyricsListItem>>>() {

                @Override
                public LiveData<List<SongLyricsListItem>> apply(List<SongLyricsListItem> input) {
                    return filter(input);
                }
            });
            needsToFetchItems = true;
        }
        if (mSortOrder != sortOrder) {
            mSortOrder = sortOrder;
            needsToFetchItems = true;
        }
        if (needsToFetchItems || forceRefresh) {
            loadSongLyricListItems();
        }
        return mFilteredSongLyricListItems;
    }

    private LiveData<List<SongLyricsListItem>> filter(List<SongLyricsListItem> input) {

        if (searchQuery != null && input != null) {
            WeakReference<SongLyricsListViewModel> self = new WeakReference<>(this);
            //noinspection unchecked
            new FilterListItemAsyncTask(self)
                    .execute(input);
        } else {
            mFilterSongLyricListItemsWorker.setValue(input);
        }
        return mFilterSongLyricListItemsWorker;
    }

    public void setSongLyricsDao(SongLyricsDao songLyricsDao) {
        mSongLyricsDao = songLyricsDao;
    }

    public SongLyricsDao getSongLyricsDao() {
        return mSongLyricsDao;
    }

    /**
     * Asynchronously load {@literal List<SongLyricListItems>} as LiveData stream
     */
    @SuppressWarnings("ConstantConditions")
    private void loadSongLyricListItems() {
        SongLyricsDao songLyricsDao = mSongLyricsDao;

        if (songLyricsDao == null) {
            throw new IllegalStateException("SongLyricDao unset");
        }
        LiveData<List<SongLyricsListItem>> query;
        switch (mSortOrder) {
            case ORDER_ARTIST:
                query = songLyricsDao.fetchListItems_Artist();
                break;

            case ORDER_ALBUM:
                query = songLyricsDao.fetchListItems_Album();
                break;

            case ORDER_TRACK:
                query = songLyricsDao.fetchListItems_Track();
                break;

            case ORDER_RECENT:
            default:
                query = songLyricsDao.fetchListItems_NaturalOrder();
        }

        final LiveData<List<SongLyricsListItem>> query_sorted =
                query;

        SongLyricDetailItemViewModel lyricsViewModel = mSongLyricViewModel != null ?
                mSongLyricViewModel.get() : null;
        final LiveData<SongLyrics> songLyricsLiveData =
                lyricsViewModel != null ? lyricsViewModel.getSongLyrics() :
                        null;

        mSongLyricListItems.addSource(query_sorted,
                new Observer<List<SongLyricsListItem>>() {
                    @Override
                    public void onChanged(@Nullable List<SongLyricsListItem> songLyricsListItems) {
                        mSongLyricListItems.removeSource(query_sorted);
                        mSongLyricListItems.setValue(songLyricsListItems);
                    }
                });
        if (!songLyricsSourcedToListItems && songLyricsLiveData != null) {
            songLyricsSourcedToListItems = true;
            mSongLyricListItems.addSource(songLyricsLiveData, new Observer<SongLyrics>() {
                @Override
                public void onChanged(@Nullable SongLyrics songLyrics) {
                    List<SongLyricsListItem> items = mSongLyricListItems.getValue();

                    // Update the list to show the changes on the item that occurred while in detail view
                    // This is just to refresh the UI
                    if (songLyrics != null && items != null) {
                        long uid = songLyrics.getUid();

                        for (SongLyricsListItem listItem : items) {
                            if (listItem != null &&
                                    updateListItem(listItem, songLyrics, uid)) {
                                break;
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     *
     * @param listItem The list item that has a subset of the fields in songLyrics
     * @param songLyrics The songLyrics that should update the same fields in listItem
     * @param songUid The same as songLyrics.getUid(); Passing the value avoid the dereference
     * @return true iff songUi == listItem.getUid(). In the case of true, the fields of listItem
     *  are set to match the fields of songLyrics
     */
    private boolean updateListItem(@NonNull SongLyricsListItem listItem,
                                   @NonNull SongLyrics songLyrics, long songUid) {
        if (listItem.getUid() == songUid) {
            listItem.setAlbum(songLyrics.getAlbum());
            listItem.setArtist(songLyrics.getArtist());
            listItem.setTrackTitle(songLyrics.getTrackTitle());
            return true;
        }
        return false;
    }

    public void setSongLyricViewModel(SongLyricDetailItemViewModel songLyricViewModel) {
        if (mSongLyricViewModel == null) {
            mSongLyricViewModel = new WeakReference<>(songLyricViewModel);
        }
    }

    public void refreshFilter() {
        filter(mSongLyricListItems.getValue());
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    private static class FilterListItemAsyncTask extends
            AsyncTask<List<SongLyricsListItem>, Void, List<SongLyricsListItem>> {

        final private WeakReference<SongLyricsListViewModel> mListModel;
        private String query;
        private int filterId;

        public FilterListItemAsyncTask(WeakReference<SongLyricsListViewModel> songLyricsListViewModelWeakReference) {
            mListModel = songLyricsListViewModelWeakReference;
        }

        @Override
        protected void onPreExecute() {
            SongLyricsListViewModel viewModel = mListModel.get();
            if (viewModel != null) {
                query = viewModel.searchQuery;
                filterId = viewModel.filterId;
            } else {
                cancel(true);
            }
        }

        @Override
        protected List<SongLyricsListItem> doInBackground(List<SongLyricsListItem>[] lists) {
            List<SongLyricsListItem> filteredList = lists[0];

            // An improvement would be for the implementation to call this methods isCancelled()
            // while doing the work load
            filteredList = LyricActionBarHelperKt.filterSongLyricsSearch(filteredList, filterId, query);

            SongLyricsListViewModel viewModel = mListModel.get();
            if (viewModel != null) {
                viewModel.mFilterSongLyricListItemsWorker.postValue(filteredList);
            }
            return filteredList;
        }

        @Override
        protected void onCancelled(List<SongLyricsListItem> songLyricsListItems) {
            SongLyricsListViewModel viewModel = mListModel.get();
            if (viewModel != null) {
                viewModel.mFilterSongLyricListItemsWorker.setValue(songLyricsListItems);
            }
        }
    }
}