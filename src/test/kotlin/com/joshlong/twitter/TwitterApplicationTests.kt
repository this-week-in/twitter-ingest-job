package com.joshlong.twitter

import org.junit.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.web.client.RestTemplate
import twitter.BaseTwitterClient
import twitter.BearerTokenInterceptor
import java.io.InputStreamReader


class TwitterApplicationTests {

	private val tweetsJsonFile = ClassPathResource("/tweets.json")

	private val fileTweetJsonProducer: (String) -> String = {
		tweetsJsonFile.inputStream.use { inputStream ->
			InputStreamReader(inputStream).use {
				it.readText()
			}
		}
	}

	private val twitterClient = BaseTwitterClient(this.fileTweetJsonProducer)

	@Test
	fun contextLoads() {
		this.twitterClient.getUserTimeline("starbuxman").forEach {
			println(it)
		}
	}
}
