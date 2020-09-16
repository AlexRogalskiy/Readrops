package com.readrops.api.localfeed

import android.accounts.NetworkErrorException
import androidx.annotation.WorkerThread
import com.readrops.api.localfeed.json.JSONFeedAdapter
import com.readrops.api.localfeed.json.JSONItemsAdapter
import com.readrops.api.utils.LibUtils
import com.readrops.api.utils.ParseException
import com.readrops.api.utils.UnknownFormatException
import com.readrops.db.entities.Feed
import com.readrops.db.entities.Item
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection

class LocalRSSDataSource(private val httpClient: OkHttpClient) {

    /**
     * Query RSS url
     * @param url url to query
     * @param headers request headers
     * @param withItems parse items with their feed
     * @return a Feed object with its items if specified by [withItems]
     */
    @WorkerThread
    fun queryRSSResource(url: String, headers: Headers?, withItems: Boolean): Pair<Feed, List<Item>>? {
        val response = queryUrl(url, headers)

        return when {
            response.isSuccessful -> {
                val header = response.header(LibUtils.CONTENT_TYPE_HEADER)
                        ?: throw ParseException("Unable to get $url content-type")

                val contentType = LibUtils.parseContentType(header)
                        ?: throw ParseException("Unable to parse $url content-type")

                var type = LocalRSSHelper.getRSSType(contentType)

                val bodyArray = response.peekBody(Long.MAX_VALUE).bytes()

                // if we can't guess type based on content-type header, we use the content
                if (type == LocalRSSHelper.RSSType.UNKNOWN)
                    type = LocalRSSHelper.getRSSContentType(ByteArrayInputStream(bodyArray))
                // if we can't guess type even with the content, we are unable to go further
                if (type == LocalRSSHelper.RSSType.UNKNOWN) throw UnknownFormatException("Unable to guess $url RSS type")

                val feed = parseFeed(ByteArrayInputStream(bodyArray), type, response)
                val items = if (withItems) parseItems(ByteArrayInputStream(bodyArray), type) else listOf()

                response.body?.close()
                Pair(feed, items)
            }
            response.code == HttpURLConnection.HTTP_NOT_MODIFIED -> null
            else -> throw NetworkErrorException("$url returned ${response.code} code : ${response.message}")
        }
    }

    @WorkerThread
    fun isUrlRSSResource(url: String): Boolean {
        val response = queryUrl(url, null)

        return if (response.isSuccessful) {
            val contentType = response.header(LibUtils.CONTENT_TYPE_HEADER)
                    ?: return false

            var type = LocalRSSHelper.getRSSType(contentType)

            if (type == LocalRSSHelper.RSSType.UNKNOWN)
                type = LocalRSSHelper.getRSSContentType(response.body?.byteStream()!!) // stream is closed in helper method

            type != LocalRSSHelper.RSSType.UNKNOWN
        } else false
    }

    @Throws(IOException::class)
    private fun queryUrl(url: String, headers: Headers?): Response {
        val requestBuilder = Request.Builder().url(url)
        headers?.let { requestBuilder.headers(it) }

        return httpClient.newCall(requestBuilder.build()).execute()
    }

    private fun parseFeed(stream: InputStream, type: LocalRSSHelper.RSSType, response: Response): Feed {
        val feed = if (type != LocalRSSHelper.RSSType.JSONFEED) {
            val adapter = XmlAdapter.xmlFeedAdapterFactory(type)

            adapter.fromXml(stream)
        } else {
            val adapter = Moshi.Builder()
                    .add(JSONFeedAdapter())
                    .build()
                    .adapter<Feed>(Feed::class.java)

            adapter.fromJson(Buffer().readFrom(stream))!!
        }

        feed.etag = response.header(LibUtils.ETAG_HEADER)
        feed.lastModified = response.header(LibUtils.LAST_MODIFIED_HEADER)

        return feed
    }

    private fun parseItems(stream: InputStream, type: LocalRSSHelper.RSSType): List<Item> {
        return if (type != LocalRSSHelper.RSSType.JSONFEED) {
            val adapter = XmlAdapter.xmlItemsAdapterFactory(type)

            adapter.fromXml(stream)
        } else {
            val adapter = Moshi.Builder()
                    .add(Types.newParameterizedType(MutableList::class.java, Item::class.java), JSONItemsAdapter())
                    .build()
                    .adapter<List<Item>>(Types.newParameterizedType(MutableList::class.java, Item::class.java))

            adapter.fromJson(Buffer().readFrom(stream))!!
        }
    }
}