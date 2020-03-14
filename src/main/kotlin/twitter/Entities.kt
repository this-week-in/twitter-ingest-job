package twitter

import java.net.URL

data class Entities(
		val hashtags: List<Hashtag>,
		val userMentions: List<Any>,
		val urls: List<URL>
)