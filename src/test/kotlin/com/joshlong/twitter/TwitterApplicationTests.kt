package com.joshlong.twitter

import org.junit.Test
import org.springframework.web.client.RestTemplate
import twitter.BearerTokenInterceptor
import twitter.TwitterClient


class TwitterApplicationTests {

	private val authenticatedRestTemplate = RestTemplate()
			.apply {
				val apiKey = System.getenv()["TWITTER_ORGANIZER_CLIENT_KEY"]!!
				val apiKeySecret = System.getenv()["TWITTER_ORGANIZER_CLIENT_KEY_SECRET"]!!
				interceptors.add(BearerTokenInterceptor(apiKey, apiKeySecret))
			}
	private val twitterClient = TwitterClient(authenticatedRestTemplate)

	@Test
	fun contextLoads() {
		this.twitterClient.getUserTimeline("starbuxman").forEach {
			println(it)
		}
	}

}
