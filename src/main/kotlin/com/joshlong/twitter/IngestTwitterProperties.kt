package com.joshlong.twitter

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("ingest")
class IngestTwitterProperties(
		val consumerKey: String,
		val consumerSecret: String,
		var pollerRate: Long = 1000 * 60 * 15 // 15 minutes
)