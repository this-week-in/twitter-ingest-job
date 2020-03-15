package twitter

import org.springframework.web.client.RestTemplate

class HttpTwitterClient(rt: RestTemplate) : BaseTwitterClient({ username, sinceId ->
	val userTimelineUrl = "https://api.twitter.com/1.1/statuses/user_timeline.json"
	val sinceIdParam = if (sinceId > 0) "&since_id=${sinceId}" else ""
	val uri = "${userTimelineUrl}?include_rts=1&count=200&screen_name=${username}${sinceIdParam}"
	rt.getForEntity(uri, String::class.java).body!!
})