package com.joshlong.twitter.api

import org.junit.Assert
import org.junit.Test
import org.springframework.web.client.RestTemplate

class TwitterClientIntegrationTests {

	private val authenticatedRestTemplate =
			RestTemplate()
					.apply {
						val apiKey = System.getenv()["TWITTER_ORGANIZER_CLIENT_KEY"]!!
						val apiKeySecret = System.getenv()["TWITTER_ORGANIZER_CLIENT_KEY_SECRET"]!!
						interceptors.add(BearerTokenInterceptor(apiKey, apiKeySecret))
					}

	private val twitterClient = HttpTwitterClient(authenticatedRestTemplate)

	@Test
	fun `should be able to get tweets from other users`() {
		val username = "springrod"
		val timeline = this.twitterClient.getUserTimeline(username)
		Assert.assertTrue(timeline.size > 0)
		timeline.forEach {
			println(it)
		}

	}

}