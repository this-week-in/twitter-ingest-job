package com.joshlong.twitter.integration

import com.joshlong.twitter.IngestTwitterProperties
import com.joshlong.twitter.api.BaseTwitterClient
import com.joshlong.twitter.api.BearerTokenInterceptor
import com.joshlong.twitter.api.HttpTwitterClient
import com.joshlong.twitter.api.TwitterClient
import org.springframework.cloud.CloudFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.integration.context.IntegrationContextUtils
import org.springframework.integration.dsl.context.IntegrationFlowContext
import org.springframework.integration.metadata.MetadataStore
import org.springframework.web.client.RestTemplate
import pinboard.PinboardClient
import java.util.concurrent.Executor

@Configuration
class IntegrationConfiguration(private val ingestTwitterProperties: IngestTwitterProperties) {

	private val profileToTags: Map<String, List<String>> = mapOf(
			"springcentral" to listOf("spring", "pivotal", "twis", "ingest"),
			"pivotal" to listOf("pivotal", "ingest"),
			"vmware" to listOf("vmware", "ingest"),
			"JavaAtMicrosoft" to listOf("vmware", "twis", "ingest", "microsoft", "java"),
			"vmwaretanzu" to listOf("vmware", "ingest", "twis"),
			"rabbitmq" to listOf("rabbitmq", "twis", "ingest"),
			"confluentInc" to listOf("kafka", "twis", "ingest"),
			"cloudfoundry" to listOf("cloudfoundry", "twis", "cloud", "ingest"),
			"springrod" to listOf("spring", "atomist", "ingest"),
			"rob_winch" to listOf("spring", "spring-security", "security", "ingest"),
			"springframework" to listOf("spring", "twis", "ingest"),
			"springboot" to listOf("spring", "spring-boot", "twis", "ingest"),
			"springcloud" to listOf("spring", "spring-cloud", "spring-boot", "twis", "ingest"),
			"springsecurity" to listOf("spring", "twis", "spring-security", "security", "ingest"),
			"springdata" to listOf("spring", "spring-data", "twis", "ingest"),
			"java" to listOf("java", "twis", "ingest"),
			"wattersjames" to listOf("cloudfoundry", "pivotal", "spring", "ingest"),
			"olivergierke" to listOf("spring", "ingest", "spring-data", "ingest"),
			"phillip_webb" to listOf("spring", "ingest", "spring-boot", "ingest"),
			"david_syer" to listOf("spring", "ingest", "spring-boot", "spring-cloud", "ingest"),
			"spencerbgibb" to listOf("spring", "ingest", "spring-boot", "spring-cloud", "ingest")
	)

	private val authenticatedRestTemplate = RestTemplate().apply {
		val bearerTokenInterceptor = BearerTokenInterceptor(ingestTwitterProperties.consumerKey, ingestTwitterProperties.consumerSecret)
		interceptors.add(bearerTokenInterceptor)
	}

	@Bean
	@Profile("cloud")
	fun cloud() = CloudFactory().cloud

	@Bean
	@Profile("cloud")
	fun redisConnectionFactory() = cloud().getSingletonServiceConnector(RedisConnectionFactory::class.java, null)

	@Bean(IntegrationContextUtils.METADATA_STORE_BEAN_NAME)
	fun redisMetadataStore(stringRedisTemplate: StringRedisTemplate) = RedisMetadataStore(stringRedisTemplate)

	@Bean
	fun twitterClient(ingestTwitterProperties: IngestTwitterProperties): BaseTwitterClient = HttpTwitterClient(this.authenticatedRestTemplate)

	@Bean
	fun stringRedisTemplate(rcf: RedisConnectionFactory) = StringRedisTemplate(rcf)

	@Bean
	@Scope("prototype")
	fun twitterMessageSource(
			profile: String,
			metadataStore: MetadataStore, twitterClient: TwitterClient) =
			TwitterUserTimelineMessageSource(profile, metadataStore, twitterClient)

	@Bean
	fun runner(ifc: IntegrationFlowContext, pc: PinboardClient, tc: TwitterClient, metadataStore: MetadataStore, executor: Executor) =
			TwitterIngestRunner(profileToTags, ifc, pc, tc, metadataStore, executor, this)

}