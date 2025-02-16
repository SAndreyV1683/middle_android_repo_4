package com.yandex.practicum.middle_homework_4.data.news_service

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.yandex.practicum.middle_homework_4.data.database.entity.News
import com.yandex.practicum.middle_homework_4.ui.contract.NewsService
import kotlin.text.Charsets.UTF_8


class NewsServiceImpl(
    application: Context,
    fileName: String
) : NewsService {
    private var source: List<News>? = null
    private val maxItem = 10
    private val size: Int
        get() = source?.size ?: 0

    init {
        loadAssetsFromFile(application, fileName)
    }

    override fun fetchData(page: Int): NewsResponse {
        var end = page * maxItem + 1
        val start = end - maxItem - 1
        if (start >= size) return NewsResponse(nextPage = null, news = emptyList())
        if (end > size) end = size - 1
        val responseList = source?.subList(start, end) ?: emptyList()
        Log.i(TAG, "NewsService loading data : page = $page | data $responseList")
        return NewsResponse(nextPage = page + 1, news = responseList)
    }

    private fun loadAssetsFromFile(context: Context, fileName: String) {
        val inputStream = context.assets.open(fileName)
        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        val json = String(buffer, charset = UTF_8)
        val gson = Gson()
        source = gson.fromJson(json, Array<News>::class.java).toList()
    }
}
