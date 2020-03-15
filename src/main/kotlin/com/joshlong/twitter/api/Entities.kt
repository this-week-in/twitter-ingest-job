package com.joshlong.twitter.api

import java.net.URL

data class Entities(
		val hashtags: List<Hashtag>,
		val userMentions: List<UserMention>,
		val urls: List<URL>
)