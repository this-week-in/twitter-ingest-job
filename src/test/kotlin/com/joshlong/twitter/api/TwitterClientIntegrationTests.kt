package com.joshlong.twitter.api

import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.Disabled
import org.springframework.web.client.RestTemplate

@Disabled
class TwitterClientIntegrationTests {

	private val authenticatedRestTemplate =
			RestTemplate()
					.apply {
						val mutableMap = System.getenv()
						fun keyIfItExists(key: String): String = if (mutableMap.containsKey(key)) mutableMap[key]!! else ""
						val apiKey = keyIfItExists("TWITTER_ORGANIZER_CLIENT_KEY")
						val apiKeySecret = keyIfItExists("TWITTER_ORGANIZER_CLIENT_KEY_SECRET")
						interceptors.add(BearerTokenInterceptor(apiKey, apiKeySecret))
					}

	private val twitterClient = HttpTwitterClient(authenticatedRestTemplate)

	@Test
	fun `should be able to get tweets from other users`() {
		val username = "SpringCentral"
		val timeline = this.twitterClient.getUserTimeline(username)
		Assert.assertTrue(timeline.isNotEmpty())
		timeline.forEach {
			println(it)
		}

	}

}