package com.joshlong.twitter.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.logging.LogFactory
import java.lang.Boolean
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


/**
 * A client to read tweets from a given user's timeline.
 *
 * @author Josh Long
 * @see <a href="https://developer.twitter.com/en/docs/basics/authentication/oauth-2-0/bearer-tokens">how to do OAuth authentication for Twitter's API</a>
 * @see <a href="https://developer.twitter.com/en/docs/tweets/timelines/com.joshlong.twitter.api-reference/get-statuses-user_timeline">the API for a user's timeline</a>
 */
open class BaseTwitterClient(
		private val rateLimitStatusProducer: () -> String,
		private val tweetProducer: (String, Long) -> String) : TwitterClient {

	private val log = LogFactory.getLog(BaseTwitterClient::class.java)
	private val formatterString = "EEE MMM d HH:mm:ss ZZ yyyy"
	private val objectMapper = ObjectMapper()

	private val formatter = SimpleDateFormat(formatterString)

	override fun getUserTimeline(username: String, sinceId: Long): List<Tweet> = parseJson(tweetProducer(username, sinceId))

	// todo incorporate the rate limiter insight when returning requests.
	//  \for now the trick is to only run the SI poller every 15 minutes,
	//  \which is the rate limiter window time anyway

	private fun getRateLimiterStatusForUserTimeline() =
			getRateLimiterStatusForFamily("statuses", "/statuses/user_timeline")

	private fun getRateLimiterStatusForFamily(family: String, endpoint: String): RateLimitStatus {
		val json = rateLimitStatusProducer()
		val jsonNode = objectMapper.readTree(json)
		val rlJson = jsonNode["resources"][family][endpoint]
		val limit = rlJson["limit"].intValue()
		val remaining = rlJson["remaining"].intValue()
		val reset = rlJson["reset"].longValue()
		return RateLimitStatus(limit, remaining, reset)
	}

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

	private fun log(msg: String) {
		if (log.isDebugEnabled) log.debug(msg)
	}

	private fun parseJson(json: String): List<Tweet> {
		val tweets = mutableListOf<Tweet>()
		val jsonNode: JsonNode = objectMapper.readTree(json)

		jsonNode.forEach { tweetNode ->
			val createdAt: Date =
					if (tweetNode.has("created_at")) {
						val textValue = tweetNode["created_at"].textValue()
//						log("the value is $textValue")
						try {
							synchronized(this.formatter) {
								val d = formatter.parse(textValue)
								d
							}
						} catch (ex: Exception) {
							log("couldn't parse $textValue!")
							Date()
						}
					} else {
						log("there is no date!")
						Date()
					}

			val tweet = Tweet(
					createdAt,
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