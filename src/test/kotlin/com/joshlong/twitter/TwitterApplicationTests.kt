package com.joshlong.twitter

import org.junit.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.web.client.RestTemplate
import twitter.BearerTokenInterceptor
import twitter.BaseTwitterClient
import twitter.HttpTwitterClient
import java.io.FileReader


class TwitterApplicationTests {

	private val tweetsJsonFile = ClassPathResource ("/")

	private val fileTweetJsonProducer: (String) -> String = {
		FileReader ("${System.getProperty("user.home")}/Desktop/tweets.json").use {
			it.readText()
		}
	}


	private val authenticatedRestTemplate = RestTemplate()
			.apply {
				val apiKey = System.getenv()["TWITTER_ORGANIZER_CLIENT_KEY"]!!
				val apiKeySecret = System.getenv()["TWITTER_ORGANIZER_CLIENT_KEY_SECRET"]!!
				interceptors.add(BearerTokenInterceptor(apiKey, apiKeySecret))
			}
	private val twitterClient = BaseTwitterClient(authenticatedRestTemplate)

	@Test
	fun contextLoads() {
		this.twitterClient.getUserTimeline("starbuxman").forEach {
			println(it)
		}
	}

}
