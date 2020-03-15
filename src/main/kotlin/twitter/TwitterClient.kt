package twitter

interface TwitterClient {

	/**
	 * Returns all the tweets for a given user, optionally
	 * restricting it to a last message received.
	 */
	fun getUserTimeline(username: String, sinceId: Long = -1): List<Tweet>

}