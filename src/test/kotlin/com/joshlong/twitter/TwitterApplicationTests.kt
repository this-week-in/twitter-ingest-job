package com.joshlong.twitter

import org.junit.Test
import org.springframework.core.io.ClassPathResource
import twitter.BaseTwitterClient
import java.io.InputStreamReader


class TwitterApplicationTests {

	private val tweetsJsonFile = ClassPathResource("/tweets.json")

	private val fileTweetJsonProducer: (String, Long) -> String = { _, _ ->
		tweetsJsonFile.inputStream.use { inputStream ->
			InputStreamReader(inputStream).use {
				it.readText()
			}
		}
	}

	private val twitterClient = BaseTwitterClient(this.fileTweetJsonProducer)

	@Test
	fun contextLoads() {
		val timeline = this.twitterClient.getUserTimeline("starbuxman")
		println(timeline.size)
		timeline.forEach {
			println(it)
		}
	}
}
