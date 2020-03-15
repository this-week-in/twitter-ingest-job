package com.joshlong.twitter.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.FileWriter
import java.lang.Boolean
import java.net.URL
import java.text.SimpleDateFormat


/**
 * A client to read tweets from a given user's timeline.
 *
 * @author Josh Long
 * @see <a href="https://developer.twitter.com/en/docs/basics/authentication/oauth-2-0/bearer-tokens">how to do OAuth authentication for Twitter's API</a>
 * @see <a href="https://developer.twitter.com/en/docs/tweets/timelines/com.joshlong.twitter.api-reference/get-statuses-user_timeline">the API for a user's timeline</a>
 */
open class BaseTwitterClient(private val tweetProducer: (String, Long) -> String) : TwitterClient {

	private val formatter = SimpleDateFormat("EEE MMM d HH:mm:ss ZZ yyyy")
	private val objectMapper = ObjectMapper()

	override fun getUserTimeline(username: String, sinceId: Long): List<Tweet> = parseJson(tweetProducer(username, sinceId))

	private fun <T> collectionFromAttribute(json: JsonNode, attribute: String, extractor: (JsonNode) -> T): List<T> =
			if (!json.has(attribute)) emptyList() else json[attribute].map { extractor(it) }

	private fun buildUserMentions(json: JsonNode) = collectionFromAttribute(json, "user_mentions") {
		UserMention(it["screen_name"].textValue(), it["name"].textValue(), java.lang.Long.parseLong(it["id_str"].textValue()))
	}

	private fun buildHashtags(json: JsonNode) = collectionFromAttribute(json, "hashtags") { Hashtag(it["text"].textValue()) }

	private fun buildUrls(json: JsonNode) = collectionFromAttribute(json, "urls") { URL(it["expanded_url"].textValue()) }

	private fun buildEntities(json: JsonNode) = Entities(buildHashtags(json), buildUserMentions(json), buildUrls(json))

	private fun buildUser(jsonNode: JsonNode): User {
		val url: String? = jsonNode["url"]?.textValue()
		return User(
				java.lang.Long.parseLong(jsonNode["id_str"].textValue()),
				jsonNode["name"].textValue(),
				jsonNode["screen_name"].textValue(),
				jsonNode["location"].textValue(),
				jsonNode["description"].textValue(),
				if (url != null) URL(url) else null
		)
	}

	private fun logJson(str: String) {
		FileWriter("${System.getenv()["HOME"]}/Desktop/tweets.json").use {
			it.write(str)
		}
	}

	private fun parseJson(json: String): List<Tweet> {
		val tweets = mutableListOf<Tweet>()
		val jsonNode: JsonNode = objectMapper.readTree(json)
		logJson(jsonNode.textValue())
		jsonNode.forEach { tweetNode ->
			val tweet = Tweet(
					this.formatter.parse(tweetNode["created_at"].textValue()),
					java.lang.Long.parseLong(tweetNode["id_str"].textValue()),
					tweetNode["text"].textValue(),
					Boolean.parseBoolean(tweetNode["truncated"].textValue()),
					tweetNode["in_reply_to_status_id_str"].textValue(),
					buildEntities(tweetNode["entities"]),
					buildUser(tweetNode["user"])
			)
			tweets.add(tweet)
		}
		return tweets
	}
}