package twitter

import java.util.*

data class Tweet(
		val createdAt: Date,
		val id: String,
		val text: String,
		val truncated: Boolean,
		val inReplyToStatusId: String?,
		val entities: Entities,
		val user: User
)