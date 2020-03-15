package com.joshlong.twitter

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

// todo we need to restore the use of these variables.
@ConfigurationProperties("ingest")
@ConstructorBinding
class IngestTwitterProperties(
		val consumerKey: String,
		val consumerSecret: String,
		var pollerRate: Long = 1000 * 60 * 15 // 15 minutes
)