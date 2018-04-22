package com.example.john.lyricsbuddy;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.List;

import static com.example.john.lyricsbuddy.LyricDatabaseHelper.*;

/**
 * Created by john on 4/14/18.
 * ViewModel that holds LiveData that fetches song lyric list items using Room
 * This ViewModel works in tandem with SongLyricDetailItemViewModel; as such, they
 * should be given the context of the same activity
 */

public class SongLyricsListViewModel extends ViewModel {
    private SongLyricsDao mSongLyricsDao;
    private MediatorLiveData<List<SongLyricsListItem>> mSongLyricListItems;
    private int mSortOrder;
    /**
     * Allows this view model to listen to changes in liveData in the detail view model
     */
    private SongLyricDetailItemViewModel mSongLyricViewModel;
    /**
     * Flag to indicating LiveData&lt;SongLyric> has been set as source {@link #mSongLyricListItems}
     */
    private boolean songLyricsSourcedToListItems;

    public static final int ORDER_RECENT    = 0;
    public static final int ORDER_ARTIST    = 1;
    public static final int ORDER_ALBUM     = 2;
    public static final int ORDER_TRACK     = 3;

    public SongLyricsListViewModel() {
        mSortOrder = ORDER_RECENT;
        songLyricsSourcedToListItems = false;
    }

    public LiveData<List<SongLyricsListItem>> getLyricList() {
        return getLyricList(mSortOrder, false);
    }

    // TODO re-fetch the lyric list for updates to the lyric record, in certain cases
    public LiveData<List<SongLyricsListItem>> getLyricList(boolean forceRefresh) {
        return getLyricList(mSortOrder, forceRefresh);
    }

    public LiveData<List<SongLyricsListItem>> getLyricList(int sortOrder, boolean forceRefresh) {
        boolean needsToFetchItems = false;

        if (mSongLyricListItems == null) {
            mSongLyricListItems = new MediatorLiveData<>();
            needsToFetchItems = true;
        }
        if (mSortOrder != sortOrder) {
            mSortOrder = sortOrder;
            needsToFetchItems = true;
        }
        if (needsToFetchItems || forceRefresh) {
            Log.d("Database", "Need to fetch LyricListItems as LiveData");
            loadSongLyricListItems();
        } else {
            Log.d("Database", "Song lyric list items already populated");
        }
        return mSongLyricListItems;
    }

    public void setSongLyricsDao(SongLyricsDao songLyricsDao) {
        mSongLyricsDao = songLyricsDao;
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
        final LiveData<SongLyrics> songLyricsLiveData =
                mSongLyricViewModel != null ? mSongLyricViewModel.getSongLyrics() :
                        null;
        final SongLyrics staticSongLyrics =
                mSongLyricViewModel != null ? mSongLyricViewModel.getSongLyricsInstantly() : null;

        mSongLyricListItems.addSource(query_sorted,
                new Observer<List<SongLyricsListItem>>() {
                    @Override
                    public void onChanged(@Nullable List<SongLyricsListItem> songLyricsListItems) {
                        mSongLyricListItems.removeSource(query_sorted);

                        // Apply value of static value of SongLyrics; its value in the viewModel
                        // has since changed, but it is not updated back to the database on
                        // which query occurred
                        if (staticSongLyrics != null && songLyricsListItems != null) {
                            long uid = staticSongLyrics.getUid();
                            MATCHING_LIST_ITEM:
                            {
                                for (SongLyricsListItem listItem : songLyricsListItems) {
                                    if (listItem != null &&
                                            updateListItem(listItem, staticSongLyrics, uid)) {
                                        break MATCHING_LIST_ITEM;
                                    }
                                }
                                if (!staticSongLyrics.isBlankType1()) {
                                    songLyricsListItems.add(0,
                                            new SongLyricsListItem(staticSongLyrics));
                                }
                            }
                        }
                        mSongLyricListItems.setValue(songLyricsListItems);
                    }
                });
        if (!songLyricsSourcedToListItems && songLyricsLiveData != null) {
            songLyricsSourcedToListItems = true;
            mSongLyricListItems.addSource(songLyricsLiveData, new Observer<SongLyrics>() {
                @Override
                public void onChanged(@Nullable SongLyrics songLyrics) {
                    List<SongLyricsListItem> items = mSongLyricListItems.getValue();

                    if (songLyrics != null && items != null) {
                        long uid = songLyrics.getUid();
                        MATCHING_LIST_ITEM:
                        {
                            for (SongLyricsListItem listItem : items) {
                                if (listItem != null &&
                                        updateListItem(listItem, songLyrics, uid)) {
                                    break MATCHING_LIST_ITEM;
                                }
                            }
                            if (!songLyrics.isBlankType1()) {
                                items.add(0, new SongLyricsListItem(songLyrics));
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
        mSongLyricViewModel = songLyricViewModel;
    }
}