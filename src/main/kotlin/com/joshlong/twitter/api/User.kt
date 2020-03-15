package com.joshlong.twitter.api

import java.net.URL

data class User(
		val id: Long,
		val name: String,
		val screenName: String,
		val location: String,
		val description: String,
		val url: URL?
)