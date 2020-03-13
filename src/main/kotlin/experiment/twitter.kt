package experiment

import com.fasterxml.jackson.databind.JsonNode
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
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference

@SpringBootApplication
class TwitterClientApplication {


	@Bean
	fun authenticatedRestTemplate(env: Environment) = RestTemplate()
			.apply {
				val apiKey = env["TWITTER_ORGANIZER_CLIENT_KEY"]!!
				val apiKeySecret = env["TWITTER_ORGANIZER_CLIENT_KEY_SECRET"]!!
				interceptors.add(BearerTokenInterceptor(apiKey, apiKeySecret))
			}

	@Bean
	fun twitterClient(rt: RestTemplate) = TwitterClient(rt)
}


//https://developer.twitter.com/en/docs/basics/authentication/oauth-2-0/bearer-tokens
//https://developer.twitter.com/en/docs/tweets/timelines/api-reference/get-statuses-user_timeline
class TwitterClient(
		private val restTemplate: RestTemplate) : ApplicationListener<ApplicationReadyEvent> {

	private val userTimelineUrl = "https://api.twitter.com/1.1/statuses/user_timeline.json"

	private fun timelineFor(username: String) {
		val uri = "${userTimelineUrl}?screen_name=${username}"
		val entity = this.restTemplate.getForEntity(uri, String::class.java)
		println(entity.body)
	}

	override fun onApplicationEvent(event: ApplicationReadyEvent) {
		timelineFor("starbuxman")
		timelineFor("springrod")
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

	private fun obtainToken(
			restTemplate: RestTemplate,
			key: String,
			secret: String): String {

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