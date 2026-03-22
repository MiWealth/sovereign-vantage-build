package com.miwealth.sovereignvantage.core.ai.macro

import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * SOVEREIGN VANTAGE V5.17.0 - RSS FEED PARSER
 * Fetches and parses RSS feeds from central banks and financial news
 * © 2025-2026 MiWealth Pty Ltd
 */
class RSSFeedParser {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        // Central Bank RSS Feeds
        val RSS_FEEDS = mapOf(
            CentralBank.FED to listOf(
                "https://www.federalreserve.gov/feeds/press_all.xml",
                "https://www.federalreserve.gov/feeds/press_monetary.xml"
            ),
            CentralBank.ECB to listOf(
                "https://www.ecb.europa.eu/rss/press.html",
                "https://www.ecb.europa.eu/rss/fxref-usd.html"
            ),
            CentralBank.RBA to listOf(
                "https://www.rba.gov.au/rss/rss-cb-media-releases.xml",
                "https://www.rba.gov.au/rss/rss-cb-speeches.xml"
            ),
            CentralBank.BOE to listOf(
                "https://www.bankofengland.co.uk/rss/news"
            ),
            CentralBank.BOJ to listOf(
                "https://www.boj.or.jp/en/rss/whatsnew.xml"
            )
        )
        
        // Financial News RSS
        val NEWS_FEEDS = listOf(
            "https://feeds.reuters.com/reuters/businessNews" to "Reuters",
            "https://feeds.bloomberg.com/markets/news.rss" to "Bloomberg"
        )
        
        private const val TIMEOUT_MS = 15000
        private val DATE_FORMATS = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
        )
    }
    
    /**
     * Fetch all central bank feeds
     */
    suspend fun fetchAllCentralBankFeeds(): List<RSSFeedItem> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<RSSFeedItem>()
        
        RSS_FEEDS.entries.map { (bank, urls) ->
            async {
                urls.flatMap { url ->
                    try {
                        fetchFeed(url, bank.name, bank)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
        }.awaitAll().forEach { allItems.addAll(it) }
        
        allItems.sortedByDescending { it.pubDate }
    }
    
    /**
     * Fetch feeds for a specific central bank
     */
    suspend fun fetchBankFeed(bank: CentralBank): List<RSSFeedItem> = withContext(Dispatchers.IO) {
        val urls = RSS_FEEDS[bank] ?: return@withContext emptyList()
        
        urls.flatMap { url ->
            try {
                fetchFeed(url, bank.name, bank)
            } catch (e: Exception) {
                emptyList()
            }
        }.sortedByDescending { it.pubDate }
    }
    
    /**
     * Fetch financial news feeds (for general macro sentiment)
     */
    suspend fun fetchNewsFeeds(): List<RSSFeedItem> = withContext(Dispatchers.IO) {
        NEWS_FEEDS.map { (url, source) ->
            async {
                try {
                    fetchFeed(url, source, detectBankFromContent = true)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten().sortedByDescending { it.pubDate }
    }
    
    /**
     * Fetch and parse a single RSS feed
     */
    private suspend fun fetchFeed(
        feedUrl: String,
        source: String,
        bank: CentralBank? = null,
        detectBankFromContent: Boolean = false
    ): List<RSSFeedItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<RSSFeedItem>()
        
        try {
            val connection = URL(feedUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "SovereignVantage/5.5.51")
            
            val xml = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            
            var eventType = parser.eventType
            var currentItem: MutableMap<String, String>? = null
            var currentTag = ""
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag == "item" || currentTag == "entry") {
                            currentItem = mutableMapOf()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        currentItem?.let {
                            val text = parser.text?.trim() ?: ""
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "title" -> it["title"] = text
                                    "link" -> it["link"] = text
                                    "description", "summary", "content" -> it["description"] = text
                                    "pubDate", "published", "updated" -> it["pubDate"] = text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if ((parser.name == "item" || parser.name == "entry") && currentItem != null) {
                            val title = currentItem["title"] ?: ""
                            val detectedBank = if (detectBankFromContent) {
                                detectCentralBank(title + " " + (currentItem["description"] ?: ""))
                            } else bank
                            
                            items.add(RSSFeedItem(
                                title = title,
                                link = currentItem["link"] ?: "",
                                description = stripHtml(currentItem["description"] ?: ""),
                                pubDate = parseDate(currentItem["pubDate"] ?: ""),
                                source = source,
                                centralBank = detectedBank
                            ))
                            currentItem = null
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Log error but don't crash
        }
        
        items
    }
    
    /**
     * Detect which central bank a news item relates to
     */
    private fun detectCentralBank(content: String): CentralBank? {
        val lower = content.lowercase()
        return when {
            lower.contains("federal reserve") || lower.contains("fed ") || 
            lower.contains("fomc") || lower.contains("powell") -> CentralBank.FED
            
            lower.contains("ecb") || lower.contains("european central") || 
            lower.contains("lagarde") -> CentralBank.ECB
            
            lower.contains("rba") || lower.contains("reserve bank of australia") ||
            lower.contains("bullock") -> CentralBank.RBA
            
            lower.contains("bank of england") || lower.contains("boe") ||
            lower.contains("bailey") -> CentralBank.BOE
            
            lower.contains("bank of japan") || lower.contains("boj") ||
            lower.contains("ueda") -> CentralBank.BOJ
            
            lower.contains("pboc") || lower.contains("people's bank of china") -> CentralBank.PBOC
            
            else -> null
        }
    }
    
    private fun parseDate(dateStr: String): Long {
        if (dateStr.isEmpty()) return System.currentTimeMillis()
        
        for (format in DATE_FORMATS) {
            try {
                return format.parse(dateStr)?.time ?: continue
            } catch (e: Exception) {
                continue
            }
        }
        return System.currentTimeMillis()
    }
    
    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
                   .replace("&nbsp;", " ")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .trim()
    }
    
    fun shutdown() {
        scope.cancel()
    }
}
