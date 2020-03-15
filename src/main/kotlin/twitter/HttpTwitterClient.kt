package twitter

import org.springframework.web.client.RestTemplate

class HttpTwitterClient(rt: RestTemplate) : BaseTwitterClient ( {
	val userTimelineUrl = "https://api.twitter.com/1.1/statuses/user_timeline.json"
	val uri = "${userTimelineUrl}?screen_name=${it}"
	rt.getForEntity(uri, String::class.java).body!!
})