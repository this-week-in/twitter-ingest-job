package twitter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.web.client.RestTemplate
import java.lang.Boolean
import java.net.URL
import java.text.SimpleDateFormat

//https://developer.twitter.com/en/docs/basics/authentication/oauth-2-0/bearer-tokens
//https://developer.twitter.com/en/docs/tweets/timelines/api-reference/get-statuses-user_timeline
class TwitterClient(private val restTemplate: RestTemplate) : ApplicationListener<ApplicationReadyEvent> {

	private val userTimelineUrl = "https://api.twitter.com/1.1/statuses/user_timeline.json"

	private fun <T> collectionFromAttribute(json: JsonNode, attribute: String, extractor: (JsonNode) -> T): List<T> =
			if (!json.has(attribute)) emptyList() else json[attribute].map { extractor(it) }

	private fun buildHashtags(json: JsonNode) = collectionFromAttribute(json, "hashtags") { Hashtag(it["text"].textValue()) }

	private fun buildUserMentions(json: JsonNode) = collectionFromAttribute(json, "user_mentions") {
		UserMention(it["screen_name"].textValue(), it["name"].textValue(), it["id_str"].textValue())
	}

	private fun buildUrls(json: JsonNode) = collectionFromAttribute(json, "urls") { URL(it["expanded_url"].textValue()) }

	private fun buildEntities(json: JsonNode) = Entities(buildHashtags(json), buildUserMentions(json), buildUrls(json))

	private fun <T> readJsonProperty(
			json: JsonNode, attr: String, converter: (JsonNode) -> T, default: () -> T) = if (json.has(attr)) converter(json[attr]) else default()

	private fun buildUser(jsonNode: JsonNode): User {
		val url = readJsonProperty(jsonNode, "url", { URL(it.textValue()) }, { null })
		return User(
				jsonNode["id_str"].textValue(),
				jsonNode["name"].textValue(),
				jsonNode["screen_name"].textValue(),
				jsonNode["location"].textValue(),
				jsonNode["description"].textValue(),
				url
		)
	}

	private fun parseJson(json: String): List<Tweet> {
		val tweets = mutableListOf<Tweet>()
		val jsonNode: JsonNode = objectMapper.readTree(json)
		jsonNode.forEach { tweetNode ->
			println(tweetNode.toPrettyString())
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

	private val formatter = SimpleDateFormat("EEE MMM d HH:mm:ss ZZ yyyy")
	private val objectMapper = ObjectMapper()

	fun getUserTimeline(username: String): List<Tweet> {
		val uri = "${userTimelineUrl}?screen_name=${username}"
		val json = restTemplate.getForEntity(uri, String::class.java).body!!
		/*FileReader("${System.getProperty("user.home")}/Desktop/tweets.json").use {
			val json = it.readText()
			return parseJson(json)
		}*/
		return parseJson(json)
	}

	override fun onApplicationEvent(event: ApplicationReadyEvent) {
		getUserTimeline("starbuxman").forEach { println(it) }
	}

}