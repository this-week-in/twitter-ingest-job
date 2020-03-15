package com.joshlong.twitter.api

data class RateLimitStatus(val limit: Int, val remaining: Int, val reset: Long)