package com.joshlong.twitter.api

import java.util.*

data class Tweet(
		val createdAt: Date,
		val id: Long,
		val text: String,
		val truncated: Boolean,
		val inReplyToStatusId: String?,
		val entities: Entities,
		val user: User
)

fun Tweet.isRetweet(): Boolean = true