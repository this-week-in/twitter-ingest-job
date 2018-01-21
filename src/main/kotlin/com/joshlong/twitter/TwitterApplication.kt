package com.joshlong.twitter

import com.joshlong.jobs.watchdog.HeartbeatEvent
import org.apache.commons.logging.LogFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.CloudFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.integration.context.IntegrationContextUtils
import org.springframework.integration.dsl.IntegrationFlows
import org.springframework.integration.dsl.SourcePollingChannelAdapterSpec
import org.springframework.integration.dsl.context.IntegrationFlowContext
import org.springframework.integration.handler.GenericHandler
import org.springframework.integration.metadata.MetadataStore
import org.springframework.integration.twitter.inbound.UserTimelineMessageSource
import org.springframework.social.twitter.api.Tweet
import org.springframework.social.twitter.api.impl.TwitterTemplate
import org.springframework.stereotype.Component
import org.springframework.util.ReflectionUtils
import pinboard.PinboardClient
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.function.Consumer

/**
 * @author <a href="mailto:josh@joshlong.com">Josh Long</a>
 */
@SpringBootApplication
@EnableConfigurationProperties(IngestTwitterProperties::class)
class TwitterApplication

@Configuration
class TwitterConfiguration(val props: IngestTwitterProperties) {

	@Bean
	@Profile("cloud")
	fun cloud() = CloudFactory().cloud

	@Bean
	@Profile("cloud")
	fun redisConnectionFactory() = cloud()
			.getSingletonServiceConnector(RedisConnectionFactory::class.java, null)

	@Bean(IntegrationContextUtils.METADATA_STORE_BEAN_NAME)
	fun redisMetadataStore(rt: StringRedisTemplate) = RedisMetadataStore(rt)

	private val twitter = TwitterTemplate(props.consumerKey, props.consumerSecret,
			props.accessToken, props.accessTokenSecret)

	@Bean
	@Scope("prototype")
	fun timelineMessageSource(profile: String, id: String) =
			UserTimelineMessageSource(profile, this.twitter, id)
}

@ConfigurationProperties("ingest")
class IngestTwitterProperties(
		var consumerKey: String? = null,
		var consumerSecret: String? = null,
		var accessToken: String? = null,
		var accessTokenSecret: String? = null,
		var pollerRate: Long = 1000 * 60 * 15 // 15 minutes
)


@Component
class TwitterIngestRunner(val ifc: IntegrationFlowContext,
                          val pc: PinboardClient,
                          val twitterConfiguration: TwitterConfiguration,
                          val ingestProperties: IngestTwitterProperties) :
		ApplicationRunner, ApplicationEventPublisherAware {

	private val log = LogFactory.getLog(javaClass)

	private var publisher: ApplicationEventPublisher? = null

	override fun setApplicationEventPublisher(p0: ApplicationEventPublisher?) {
		this.publisher = p0
	}

	protected fun subscribeToTweetsFromProfile(profile: String, tags: List<String>) {
		val id = profile.filter { it.isLetterOrDigit() }
		val msgSource = this.twitterConfiguration.timelineMessageSource(profile, id)
		val flow = IntegrationFlows
				.from(msgSource, Consumer<SourcePollingChannelAdapterSpec> { it.poller({ it.fixedRate(ingestProperties.pollerRate) }) })
				.handle(GenericHandler<Tweet> { msg, headers ->
					log.debug("processing incoming tweet from ${msg.user}.")
					processTweet(profile, msg, tags)
				})
				.get()
		ifc.registration(flow)
				.id(id)
				.register()
	}

	override fun run(args: ApplicationArguments) {
		val profileToTags = mapOf(
				"springcentral" to listOf("spring", "pivotal", "twis", "ingest"),
				"pivotal" to listOf("pivotal", "ingest"),
				"cloudfoundry" to listOf("cloudfoundry", "twis", "cloud", "ingest"),
				"dzone" to listOf("programming", "ingest"),
				"springrod" to listOf("spring", "atomist", "ingest"),
				"rob_winch" to listOf("spring", "spring-security", "security", "ingest"),
				"springframework" to listOf("spring", "twis", "ingest"),
				"springboot" to listOf("spring", "spring-boot", "twis", "ingest"),
				"springcloud" to listOf("spring", "spring-cloud", "spring-boot", "twis", "ingest"),
				"springsecurity" to listOf("spring", "spring-security", "security", "ingest"),
				"springdata" to listOf("spring", "spring-data", "twis", "ingest"),
				"java" to listOf("java", "ingest"),
				"wattersjames" to listOf("cloudfoundry", "pivotal", "spring", "ingest"),
				"olivergierke" to listOf("spring", "ingest", "spring-data", "ingest"),
				"phillip_webb" to listOf("spring", "ingest", "spring-boot", "ingest"),
				"david_syer" to listOf("spring", "ingest", "spring-boot", "spring-cloud", "ingest"),
				"spencerbgibb" to listOf("spring", "ingest", "spring-boot", "spring-cloud", "ingest")
		)
		profileToTags.forEach {
			this.subscribeToTweetsFromProfile(it.key, it.value)
		}
	}

	fun processTweet(profile: String, tweet: Tweet, incomingTags: List<String>) {
		try {
			val link = "https://twitter.com/${tweet.fromUser}/status/${tweet.id}"
			val retweetedUser = tweet.retweetedStatus?.fromUser ?: ""
			val pbMsg =
					"""
						| @${tweet.fromUser} ${if (tweet.isRetweet) "re" else ""}tweeted ($link) ${if (tweet.isRetweet) "${retweetedUser}'s tweet" else ""}:
						|
						| ${tweet.text.trim()}
						|
						| ${tweet.entities.hashTags.map { "#${it.text.trim()}" }.joinToString(" ").trim()}
						|
						| ${tweet.entities.urls.map { it.expandedUrl.trim() }.joinToString(" ").trim()}
						|
						| ${tweet.entities.mentions.map { "@${it.screenName.trim()}" }.joinToString(" ").trim()}
						"""
							.trimMargin("|")
							.trim()

			if (pc.getPosts(url = link).posts.isEmpty()) {

				log.debug(pbMsg)

				val tags = mutableSetOf(profile, "ingest", "twitter")
						.apply {
							addAll(incomingTags)
							addAll(tweet.entities.hashTags.map { it.text }.toList())
						}
						.map {
							if (it.startsWith("#")) it.substring(1) else it
						}
						.map { it.toLowerCase() }

				val post = pc.addPost(url = link, description = pbMsg,
						tags = tags.toTypedArray(), dt = tweet.createdAt ?: Date(), extended = pbMsg,
						shared = false, toread = false, replace = false)
				if (post) {
					log.info("added $link ('$link') to Pinboard @ ${Instant.now().atZone(ZoneId.systemDefault())}")
				}
			}

			this.publisher!!.publishEvent(HeartbeatEvent())
		} catch (ex: Exception) {
			log.error(ex)
			ReflectionUtils.rethrowException(ex)
		}
	}
}


class RedisMetadataStore(val stringRedisTemplate: StringRedisTemplate) : MetadataStore {

	override fun put(key: String, value: String) {
		stringRedisTemplate.opsForValue().set(key, value)
	}

	override fun remove(key: String): String? =
			if (stringRedisTemplate.hasKey(key)) {
				val existingValue = stringRedisTemplate.opsForValue().get(key)
				stringRedisTemplate.delete(key)
				existingValue
			} else null

	override fun get(key: String): String? =
			if (stringRedisTemplate.hasKey(key))
				stringRedisTemplate.opsForValue().get(key)
			else null
}

fun main(args: Array<String>) {
	SpringApplicationBuilder()
			.sources(TwitterApplication::class.java)
			.run(*args)
}