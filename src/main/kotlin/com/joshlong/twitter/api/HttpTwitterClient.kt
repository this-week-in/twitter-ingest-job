package com.joshlong.twitter.api

import org.springframework.web.client.RestTemplate

class HttpTwitterClient(rt: RestTemplate) : BaseTwitterClient({
	val rateLimitStatusUrl = "https://api.twitter.com/1.1/application/rate_limit_status.json" //?resources=statuses,users
	rt.getForEntity(rateLimitStatusUrl, String::class.java).body!!
}, { username, sinceId ->
	val userTimelineUrl = "https://api.twitter.com/1.1/statuses/user_timeline.json"
	val sinceIdParam = if (sinceId > 0) "&since_id=${sinceId}" else ""
	val uri = "${userTimelineUrl}?include_rts=1&count=200&screen_name=${username}${sinceIdParam}"
	rt.getForEntity(uri, String::class.java).body!!
})