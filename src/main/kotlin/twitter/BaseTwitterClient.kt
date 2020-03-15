package twitter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.Boolean
import java.net.URL
import java.text.SimpleDateFormat

//https://developer.twitter.com/en/docs/basics/authentication/oauth-2-0/bearer-tokens
//https://developer.twitter.com/en/docs/tweets/timelines/api-reference/get-statuses-user_timeline
open class BaseTwitterClient(private val tweetProducer: (String) -> String) {

	private val formatter = SimpleDateFormat("EEE MMM d HH:mm:ss ZZ yyyy")
	private val objectMapper = ObjectMapper()

	private fun <T> collectionFromAttribute(json: JsonNode, attribute: String, extractor: (JsonNode) -> T): List<T> =
			if (!json.has(attribute)) emptyList() else json[attribute].map { extractor(it) }

	private fun buildHashtags(json: JsonNode) = collectionFromAttribute(json, "hashtags") { Hashtag(it["text"].textValue()) }

	private fun buildUserMentions(json: JsonNode) = collectionFromAttribute(json, "user_mentions") {
		UserMention(it["screen_name"].textValue(), it["name"].textValue(), it["id_str"].textValue())
	}

	private fun buildUrls(json: JsonNode) = collectionFromAttribute(json, "urls") { URL(it["expanded_url"].textValue()) }

	private fun buildEntities(json: JsonNode) = Entities(buildHashtags(json), buildUserMentions(json), buildUrls(json))

	private fun buildUser(jsonNode: JsonNode): User {
		val url: String? = jsonNode["url"]?.textValue()
		return User(
				jsonNode["id_str"].textValue(),
				jsonNode["name"].textValue(),
				jsonNode["screen_name"].textValue(),
				jsonNode["location"].textValue(),
				jsonNode["description"].textValue(),
				if (url != null) URL(url) else null
		)
	}

	private fun parseJson(json: String): List<Tweet> {
		val tweets = mutableListOf<Tweet>()
		val jsonNode: JsonNode = objectMapper.readTree(json)
		jsonNode.forEach { tweetNode ->
			val tweet = Tweet(
					this.formatter.parse(tweetNode["created_at"].textValue()),
					tweetNode["id_str"].textValue(),
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

	fun getUserTimeline(username: String): List<Tweet> = parseJson(tweetProducer(username))
}