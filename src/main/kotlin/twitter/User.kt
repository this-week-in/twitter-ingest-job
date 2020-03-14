package twitter

import java.net.URL

data class User(
		val id: String,
		val name: String,
		val screenName: String,
		val location: String,
		val description: String,
		val url: URL?
)