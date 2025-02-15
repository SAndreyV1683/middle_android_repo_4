package com.yandex.practicum.middle_homework_4.data

import android.net.http.HttpException
import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.yandex.practicum.middle_homework_4.data.database.NewsDatabase
import com.yandex.practicum.middle_homework_4.data.database.entity.News
import com.yandex.practicum.middle_homework_4.data.database.entity.RemoteKeys
import com.yandex.practicum.middle_homework_4.ui.contract.NewsService
import java.io.IOException
import java.util.concurrent.TimeUnit


@OptIn(ExperimentalPagingApi::class)
class NewsRemoteMediator(
    private val newsService: NewsService,
    private val newsDatabase: NewsDatabase,
) : RemoteMediator<Int, News>() {

    override suspend fun initialize(): InitializeAction {
        val cacheTimeout = TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS)

        return if (System.currentTimeMillis() - (newsDatabase.getRemoteKeysDao().getCreationTime()
                ?: 0) < cacheTimeout
        ) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, News>
    ): MediatorResult {
        val page: Int = when (loadType) {
            LoadType.REFRESH -> {
                val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                remoteKeys?.nextKey?.minus(1) ?: 1
            }

            LoadType.PREPEND -> {
                val remoteKeys = getRemoteKeyForFirstItem(state)
                val prevKey = remoteKeys?.prevKey
                prevKey
                    ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
            }

            LoadType.APPEND -> {
                val remoteKeys = getRemoteKeyForLastItem(state)
                val nextKey = remoteKeys?.nextKey
                nextKey
                    ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
            }
        }

        try {
            val apiResponse = newsService.fetchData(page = page)

            val news = apiResponse.news
            val endOfPaginationReached = news.isEmpty()

            newsDatabase.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    newsDatabase.getRemoteKeysDao().clearRemoteKeys()
                    newsDatabase.getNewsDao().clearAllNews()
                }
                val prevKey = if (page > 1) page - 1 else null
                val nextKey = if (endOfPaginationReached) null else page + 1
                val remoteKeys = news.map {
                    RemoteKeys(
                        newsID = it.id,
                        prevKey = prevKey,
                        currentPage = page,
                        nextKey = nextKey
                    )
                }
                newsDatabase.getRemoteKeysDao().insertAll(remoteKeys)
                newsDatabase.getNewsDao()
                    .insertAll(news.onEachIndexed { _, movie -> movie.page = page })
            }
            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (error: IOException) {
            return MediatorResult.Error(error)
        } catch (error: HttpException) {
            return MediatorResult.Error(error)
        }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(state: PagingState<Int, News>): RemoteKeys? {
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.id?.let { id ->
                newsDatabase.getRemoteKeysDao().getRemoteKeyByNewsID(id)
            }
        }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, News>): RemoteKeys? {
        return state.pages.firstOrNull {
            it.data.isNotEmpty()
        }?.data?.firstOrNull()?.let { movie ->
            return newsDatabase.getRemoteKeysDao().getRemoteKeyByNewsID(movie.id)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, News>): RemoteKeys? {
        return state.pages.lastOrNull {
            it.data.isNotEmpty()
        }?.data?.lastOrNull()?.let { movie ->
            newsDatabase.getRemoteKeysDao().getRemoteKeyByNewsID(movie.id)
        }
    }
}