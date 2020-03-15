package experiment

import com.joshlong.twitter.RedisMetadataStore
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.Lifecycle
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.integration.context.IntegrationObjectSupport
import org.springframework.integration.core.MessageSource
import org.springframework.integration.dsl.IntegrationFlows
import org.springframework.integration.metadata.MetadataStore
import org.springframework.messaging.Message
import org.springframework.messaging.MessagingException
import org.springframework.util.CollectionUtils
import org.springframework.util.StringUtils
import org.springframework.web.client.RestTemplate
import twitter.*
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer


@SpringBootApplication
class TwitterClientApplication {

	fun authenticatedRestTemplate(env: Environment) = RestTemplate()
			.apply {
				val apiKey = env["TWITTER_ORGANIZER_CLIENT_KEY"]!!
				val apiKeySecret = env["TWITTER_ORGANIZER_CLIENT_KEY_SECRET"]!!
				interceptors.add(BearerTokenInterceptor(apiKey, apiKeySecret))
			}

	@Bean
	fun twitterClient(env: Environment): BaseTwitterClient =
			HttpTwitterClient(authenticatedRestTemplate(env))

	@Bean
	fun stringRedisTemplate(rcf: RedisConnectionFactory) = StringRedisTemplate(rcf)

	@Bean
	fun redisMetadataStore(srt: StringRedisTemplate) = RedisMetadataStore(srt)

	@Bean
	fun twitterMessageSource(metadataStore: MetadataStore, twitterClient: TwitterClient) = TwitterUserTimelineMessageSource("starbuxman", metadataStore, twitterClient)

	@Bean
	fun integrationFlow(messageSource: TwitterUserTimelineMessageSource) =
			IntegrationFlows
					.from(messageSource, Consumer { pc -> pc.poller { pm -> pm.fixedRate(1_000) } })
					.handle(Tweet::class.java) { tweet, headers ->
						println("new tweet: ${tweet.text}")
						headers.forEach { println("${it.key} = ${it.value}") }
						null
					}
					.get()
}

fun main() {
	runApplication<TwitterClientApplication>()
}

class TwitterUserTimelineMessageSource(
		private val username: String,
		private val metadataStore: MetadataStore,
		private val twitterClient: TwitterClient) :
		IntegrationObjectSupport(),
		Lifecycle,
		MessageSource<Tweet> {

	private val running = AtomicBoolean(false)
	private val lastProcessedId = AtomicLong()
	private val prefetchThreshold = 0
	private val lastEnqueuedId = AtomicLong(0)
	private val metadataKey = "${TwitterClientApplication::class.java.name}.${username}"
	private val tweets = LinkedBlockingQueue<Tweet>()

	override fun isRunning(): Boolean = this.running.get()

	override fun start() {
		if (!this.running.get()) {
			val lastIdProcessedInMetadataStore = this.metadataStore.get(this.metadataKey)
			if (StringUtils.hasText(lastIdProcessedInMetadataStore)) {
				val id = java.lang.Long.parseLong(lastIdProcessedInMetadataStore)
				this.lastProcessedId.set(id)
				this.lastEnqueuedId.set(id)
			}
			this.running.set(true)
		}
	}

	override fun stop() {
		this.running.set(false)
	}

	/////

	override fun receive(): Message<Tweet>? {
		var tweet: Tweet? = tweets.poll()
		if (tweet == null) {
			refreshTweetQueueIfNecessary()
			tweet = tweets.poll()
		}
		if (tweet != null) {
			lastProcessedId.set(this.getIdForTweet(tweet))
			metadataStore.put(metadataKey, lastProcessedId.toString())
			return messageBuilderFactory.withPayload<Tweet>(tweet).build()
		}
		return null
	}

	private fun enqueueAll(tweets: List<Tweet>) =
			tweets.sortedWith(this.tweetDateComparator).forEach { enqueue(it) }

	private val tweetDateComparator: Comparator<Tweet> = kotlin.Comparator { one, two ->
		one.createdAt.compareTo(two.createdAt)
	}

	private fun getIdForTweet(tweet: Tweet) = tweet.id

	private fun enqueue(tweet: Tweet) {
		val id = this.getIdForTweet(tweet)
		if (id > this.lastEnqueuedId.get()) {
			this.tweets.add(tweet)
			this.lastEnqueuedId.set(id)
		}
	}

	private fun refreshTweetQueueIfNecessary() {
		try {
			if (tweets.size <= this.prefetchThreshold) {
				val tweets: List<Tweet> = twitterClient.getUserTimeline(username, lastEnqueuedId.get())
				if (!CollectionUtils.isEmpty(tweets)) {
					enqueueAll(tweets)
				}
			}
		} catch (exception: Exception) {
			throw MessagingException("failed while polling Twitter", exception)
		}
	}

}