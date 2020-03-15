package com.joshlong.twitter.integration

import com.joshlong.jobs.watchdog.HeartbeatEvent
import com.joshlong.twitter.api.Tweet
import com.joshlong.twitter.api.TwitterClient
import com.joshlong.twitter.api.isRetweet
import org.apache.commons.logging.LogFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.integration.dsl.IntegrationFlows
import org.springframework.integration.dsl.SourcePollingChannelAdapterSpec
import org.springframework.integration.dsl.context.IntegrationFlowContext
import org.springframework.integration.metadata.MetadataStore
import org.springframework.util.ReflectionUtils
import pinboard.PinboardClient
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.concurrent.Executor

class TwitterIngestRunner(
		val profileToTags: Map<String, List<String>>,
		val ifc: IntegrationFlowContext,
		val pc: PinboardClient,
		val tc: TwitterClient,
		val metadataStore: MetadataStore,
		val executor: Executor,
		val twitterConfiguration: TwitterConfiguration) :
		ApplicationRunner, ApplicationEventPublisherAware {

	private val log = LogFactory.getLog(javaClass)
	private var publisher: ApplicationEventPublisher? = null


	protected fun subscribeToTweetsFromProfile(profile: String, tags: List<String>) {

		executor.execute {
			val id = profile.filter { it.isLetterOrDigit() }
			log.info("${profile} subscription thread started at ${Instant.now().atZone(ZoneId.systemDefault())}")
			val msgSource = this.twitterConfiguration.twitterMessageSource(profile, metadataStore, tc)
			val flow = IntegrationFlows.from(msgSource, Consumer<SourcePollingChannelAdapterSpec> { it.poller({ it.fixedRate(60 * 15 * 1000) }) })
					.handle(GenericHandler<Tweet> { msg, headers ->
						processTweet(profile, msg, tags)
					})
					.get()
			ifc.registration(flow)
					.id(id)
					.register()
			log.info("${profile} subscription thread finished at ${Instant.now().atZone(ZoneId.systemDefault())}")
		}
	}

	override fun run(args: ApplicationArguments) {
		profileToTags.forEach {
			this.subscribeToTweetsFromProfile(it.key, it.value)
		}
	}

	fun processTweet(profile: String, tweet: Tweet, incomingTags: List<String>) {
		log.debug("processing incoming tweet from @${tweet.user.screenName}..")
		val link = "https://twitter.com/${tweet.user.screenName}/status/${tweet.id}"
		val retweetedUser = ""
		val pbMsg =
				"""
						|@${tweet.user.screenName} ${if (tweet.isRetweet()) "re" else ""}tweeted ($link) ${if (tweet.isRetweet()) "${retweetedUser}'s tweet" else ""}:
						|
						|${tweet.text.trim()}
						|
						|${tweet.entities.hashtags.map { "#${it.text.trim()}" }.joinToString(" ").trim()}
						|
						|${tweet.entities.urls.map { it.toString() }.joinToString(" ").trim()}
						|
						|${tweet.entities.userMentions.map { it.screenName }.joinToString(" ").trim()}
				"""
						.trimMargin("|")
						.trim()
		try {
			if (pc.getPosts(url = link).posts.isEmpty()) {

				log.debug("fetched $link .")
				log.debug(pbMsg)

				val tags = mutableSetOf(profile, "ingest", "com/joshlong/twitter/api")
						.apply {
							addAll(incomingTags)
							addAll(tweet.entities.hashtags.map { it.text }.toList())
						}
						.map {
							if (it.startsWith("#")) it.substring(1) else it
						}
						.map { it.toLowerCase() }

				log.debug("about to call addPost() for URL $link")
				val date = tweet.createdAt ?: Date()
				val post = pc.addPost(url = link, description = link,
						tags = tags.toTypedArray(), dt = date, extended = pbMsg,
						shared = false, toread = false, replace = false)
				if (post) {
					log.debug("added $link to Pinboard @ ${Instant.now().atZone(ZoneId.systemDefault())}")
				}
			} else {
				log.debug("processed ${link} already.")
			}
			this.publisher!!.publishEvent(HeartbeatEvent())
		} catch (ex: Exception) {
			log.error("couldn't process $link", ex)
			ReflectionUtils.rethrowException(ex)
		}
	}

	override fun setApplicationEventPublisher(applicationEventPublisher: ApplicationEventPublisher) {
		this.publisher = applicationEventPublisher
	}
}