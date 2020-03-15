package experiment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.support.beans
import org.springframework.core.env.Environment
import org.springframework.core.env.get
import org.springframework.web.client.RestTemplate
import twitter.BearerTokenInterceptor
import twitter.BaseTwitterClient
import twitter.HttpTwitterClient

@SpringBootApplication
class TwitterClientApplication {

	fun authenticatedRestTemplate(env: Environment) = RestTemplate()
			.apply {
				val apiKey = env["TWITTER_ORGANIZER_CLIENT_KEY"]!!
				val apiKeySecret = env["TWITTER_ORGANIZER_CLIENT_KEY_SECRET"]!!
				interceptors.add(BearerTokenInterceptor(apiKey, apiKeySecret))
			}

	@Bean
	fun twitterClient(env: Environment) : BaseTwitterClient = HttpTwitterClient(authenticatedRestTemplate(env) )
}

fun main() {
	runApplication<TwitterClientApplication>() {
		addInitializers(beans {
			bean {
				ApplicationListener <ApplicationReadyEvent> {
					val httpTwitter = ref<HttpTwitterClient>()
					httpTwitter.getUserTimeline("starbuxman").forEach {
						println ( "the tweet is $it")
					}
				}
			}
		})
	}
}