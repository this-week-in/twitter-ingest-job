package twitter

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