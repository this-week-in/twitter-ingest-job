package com.joshlong.twitter.integration

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.joshlong.twitter.api.TwitterClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.integration.context.IntegrationContextUtils
import org.springframework.integration.dsl.context.IntegrationFlowContext
import org.springframework.integration.metadata.MetadataStore
import pinboard.PinboardClient
import java.net.URL
import java.util.*
import java.util.concurrent.Executor
import javax.annotation.PostConstruct

@EnableConfigurationProperties(IngestProperties::class)
@Configuration
class IntegrationConfiguration {

    /*
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
*/
    @Bean(IntegrationContextUtils.METADATA_STORE_BEAN_NAME)
    fun redisMetadataStore(stringRedisTemplate: StringRedisTemplate) = RedisMetadataStore(stringRedisTemplate)

    @Bean
    fun stringRedisTemplate(rcf: RedisConnectionFactory) = StringRedisTemplate(rcf)

    @Bean
    @Scope("prototype")
    fun twitterMessageSource(
        profile: String, metadataStore: MetadataStore, twitterClient: TwitterClient
    ) = TwitterUserTimelineMessageSource(profile, metadataStore, twitterClient)

    @Bean
    fun runner(
        ifc: IntegrationFlowContext,
        pc: PinboardClient,
        tc: TwitterClient,
        metadataStore: MetadataStore,
        ip: IngestProperties,
        executor: Executor
    ) = TwitterIngestRunner(
        ip.mappings,
        ip.pollRateInSeconds,
        ifc,
        pc,
        tc,
        metadataStore,
        executor,
        this
    )

}

@ConfigurationProperties("ingest.twitter")
class IngestProperties(
    val pollRateInSeconds: Long = 1,
    var mappingsConfig: String? = null,
    val om: ObjectMapper
) {


    val mappings: Map<String, Collection<String>>
        get() {
            if (mappingsConfig != null) {
                val decoded = Base64.getDecoder().decode(mappingsConfig)
                val mappingsMap: Map<String, Collection<String>> =
                    om.readValue(decoded, object : TypeReference<Map<String, Collection<String>>>() {})
                return mappingsMap
            }

            return mutableMapOf()
        }

    @PostConstruct
    fun init() {
        println(this.mappings)
    }
}