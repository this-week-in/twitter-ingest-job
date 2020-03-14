package experiment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@SpringBootApplication
class TwitterClientApplication {


	fun authenticatedRestTemplate(env: Environment) = RestTemplate()
			.apply {
				val apiKey = env["TWITTER_ORGANIZER_CLIENT_KEY"]!!
				val apiKeySecret = env["TWITTER_ORGANIZER_CLIENT_KEY_SECRET"]!!
				interceptors.add(BearerTokenInterceptor(apiKey, apiKeySecret))
			}

	@Bean
	fun twitterClient(env: Environment) = TwitterClient(authenticatedRestTemplate(env))
}


data class Hashtag(val text: String)

data class UserMention(val screenName: String, val name: String, val id: String)

data class Entities(
		val hashtags: List<Hashtag>,
		val userMentions: List<Any>,
		val urls: List<URL>
)

data class Tweet(
		val createdAt: Date,
		val id: String,
		val text: String,
		val truncated: Boolean,
		val inReplyToStatusId: String?,
		val entities: Entities,
		val user: User
)

data class User(
		val id: String,
		val name: String,
		val screenName: String,
		val location: String,
		val description: String,
		val url: URL?
)


//https://developer.twitter.com/en/docs/basics/authentication/oauth-2-0/bearer-tokens
//https://developer.twitter.com/en/docs/tweets/timelines/api-reference/get-statuses-user_timeline
class TwitterClient(
		private val restTemplate: RestTemplate) : ApplicationListener<ApplicationReadyEvent> {

	private val userTimelineUrl = "https://api.twitter.com/1.1/statuses/user_timeline.json"

	private fun <T> collectionFromAttribute(json: JsonNode, attribute: String, extractor: (JsonNode) -> T): List<T> {
		val data = mutableListOf<T>()
		if (!json.has(attribute)) {
			return data
		}
		json[attribute].forEach {
			data.add(extractor(it))
		}
		return data
	}

	private fun buildHashtags(json: JsonNode) = collectionFromAttribute(json, "hashtags") { Hashtag(it["text"].textValue()) }

	private fun buildUserMentions(json: JsonNode): List<UserMention> {
		return collectionFromAttribute(json, "user_mentions") {
			UserMention(it["screen_name"].textValue(), it["name"].textValue(), it["id_str"].textValue())
		}
	}

	private fun buildUrls(json: JsonNode): List<URL> = collectionFromAttribute(json, "urls") { URL(it["expanded_url"].textValue()) }

	private fun buildEntities(json: JsonNode): Entities {
		return Entities(buildHashtags(json), buildUserMentions(json), buildUrls(json))
	}

	private fun <T> propertyFor(json: JsonNode,
	                            attr: String,
	                            converter: (JsonNode) -> T,
	                            default: () -> T) =
			if (json.has(attr))
				converter(json[attr])
			else default()

	private fun buildUser(jsonNode: JsonNode): User {
		val url = propertyFor(jsonNode, "url", { URL(it.textValue()) }, { null })
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
					java.lang.Boolean.parseBoolean(tweetNode["truncated"].textValue()),
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

fun main() {
	runApplication<TwitterClientApplication>()
}


class BearerTokenInterceptor(
		private val key: String, private val secret: String) : ClientHttpRequestInterceptor {

	private val accessTokenReference = AtomicReference<String>()

	// this is used only to obtain the token. it should not be the
	// same RT instance as the one being used to make OAuth-authenticated calls.
	private val accessTokenTemplate = RestTemplate()

	// todo figure out how rate limiting and timeouts interacts with this test.
	//      the tokens should be refreshed more frequently than the lifecycle of this application.
	private fun shouldRefreshToken() = this.accessTokenReference.get() == null

	override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
		val token = obtainToken(accessTokenTemplate, key, secret)
		request.headers.add(HttpHeaders.AUTHORIZATION, "Bearer $token")
		return execution.execute(request, body)
	}

	private fun obtainToken(restTemplate: RestTemplate, key: String, secret: String):
			String {

		if (!shouldRefreshToken()) {
			return this.accessTokenReference.get()
		}

		val httpHeaders = HttpHeaders(LinkedMultiValueMap())
				.apply {
					contentType = MediaType.APPLICATION_JSON
					val encodedCredentials = HttpHeaders.encodeBasicAuth(key, secret, Charset.defaultCharset())
					if (!containsKey(HttpHeaders.AUTHORIZATION)) {
						setBasicAuth(encodedCredentials);
					}
				}
		val uri = "https://api.twitter.com/oauth2/token?grant_type=client_credentials"
		val request: RequestEntity<Void> = RequestEntity.post(URI.create(uri)).headers(httpHeaders).build()
		val responseEntityJsonNode = restTemplate.postForEntity(uri, request, JsonNode::class.java)
		val jsonNode = responseEntityJsonNode.body!!
		val at = jsonNode["access_token"].textValue()
		this.accessTokenReference.set(at)
		return at
	}

}