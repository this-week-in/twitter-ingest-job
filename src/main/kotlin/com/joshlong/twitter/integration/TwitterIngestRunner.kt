package com.joshlong.twitter.integration

import com.joshlong.jobs.watchdog.HeartbeatEvent
import com.joshlong.twitter.api.Tweet
import com.joshlong.twitter.api.TwitterClient
import org.apache.commons.logging.LogFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.integration.core.GenericSelector
import org.springframework.integration.dsl.IntegrationFlows
import org.springframework.integration.dsl.context.IntegrationFlowContext
import org.springframework.integration.handler.GenericHandler
import org.springframework.integration.metadata.MetadataStore
import org.springframework.util.ReflectionUtils
import pinboard.Bookmarks
import pinboard.PinboardClient

import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.concurrent.Executor
import java.util.function.Consumer

open class TwitterIngestRunner(
    private val profileToTags: Map<String, Map<String, Collection<String>>>,
    private val pollRateInSeconds: Long,
    private val integrationFlowContext: IntegrationFlowContext,
    private val pinboardClient: PinboardClient,
    private val twitterClient: TwitterClient,
    private val metadataStore: MetadataStore,
    private val executor: Executor,
    private val integrationConfiguration: IntegrationConfiguration
) : ApplicationRunner, ApplicationEventPublisherAware {

    private val log = LogFactory.getLog(javaClass)
    private var publisher: ApplicationEventPublisher? = null

    private fun subscribeToTweetsFromProfile(profile: String, tagsAndFilters: Map<String, Collection<String>>) {

        log.info("going to register a Twitter inbound adapter for ${profile} having tags ${tagsAndFilters}")

        executor.execute {
            val id = profile.filter { it.isLetterOrDigit() }
            log.info("${profile} subscription thread started at ${Instant.now().atZone(ZoneId.systemDefault())}")
            val msgSource =
                this.integrationConfiguration.twitterMessageSource(profile, this.metadataStore, this.twitterClient)
            val flow = IntegrationFlows
                .from(
                    msgSource,
                    Consumer { it.poller { it.fixedRate(this.pollRateInSeconds * 1000) } }
                )
                .filter { tweet: Tweet ->

                    return@filter tagsAndFilters.isEmpty() || tagsAndFilters.values.any { predicates ->
                        return@filter predicates.any { predicate -> tweet.text.contains(predicate) }
                    }
                }
                .handle(GenericHandler<Tweet> { msg, _ ->
                    processTweet(profile, msg, tagsAndFilters)
                    null
                })
                .get()
            integrationFlowContext //
                .registration(flow) //
                .id(id) //
                .register()
            log.info("${profile} subscription thread finished at ${Instant.now().atZone(ZoneId.systemDefault())}")
        }
    }

    override fun run(args: ApplicationArguments) {
        profileToTags.forEach {

            this.subscribeToTweetsFromProfile(it.key, it.value)
        }
    }

    private fun processTweet(profile: String, tweet: Tweet, tagsAndFilters: Map<String, Collection<String>>) {
        log.info("processing incoming tweet from @${tweet.user.screenName}..")
        val link = "https://twitter.com/${tweet.user.screenName}/status/${tweet.id}"
        val pbMsg =
            """
					  |${link}
						|
						|@${tweet.user.screenName} 
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

            val bookmarks: Bookmarks =
                try {
                    log.debug("trying to find bookmarks for '${link}'.")
                    pinboardClient.getPosts(url = link)
                } catch (ex: java.lang.Exception) {
                    log.debug("oops! couldn't read bookmarks for link ${link}: ${ex.message}")
                    Bookmarks(Date(), null, arrayOf())
                }

            if (bookmarks.posts.isEmpty()) {

                log.debug("fetched $link .")
                log.debug(pbMsg)

                val tags = mutableSetOf<String>()
                    .apply {
                        addAll(listOf("ingest", "twitter"))

                        tagsAndFilters.forEach { t, filters ->
                            val tag = t.toLowerCase().trim()
                            for (f in filters) {
                                if (tweet.text.toLowerCase().contains(f.toLowerCase())) {
                                    add(tag)
                                }
                            }
                        }
                    }
                    .map {
                        if (it.startsWith("#")) it.substring(1) else it
                    }
                    .map { it.toLowerCase().trim() }

                log.debug("about to call addPost() for URL $link, adding the following tags: ${tags}")
                val date = tweet.createdAt
                val post = pinboardClient.addPost(
                    url = link, description = pbMsg,
                    tags = tags.toTypedArray(), dt = date, extended = pbMsg,
                    shared = false, toread = false, replace = false
                )
                if (post) {
                    log.debug("added $link to Pinboard @ ${Instant.now().atZone(ZoneId.systemDefault())}")
                }
            } else {
                log.debug("processed ${link} already.")
            }

        } catch (ex: Exception) {
            log.error("couldn't process $link", ex)
            ReflectionUtils.rethrowException(ex)
        }
        this.publisher!!.publishEvent(HeartbeatEvent())
    }

    override fun setApplicationEventPublisher(applicationEventPublisher: ApplicationEventPublisher) {
        this.publisher = applicationEventPublisher
    }
}