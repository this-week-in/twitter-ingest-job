package com.joshlong.twitter

import com.joshlong.twitter.integration.RedisMetadataStore
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.integration.context.IntegrationContextUtils

@SpringBootApplication
@EnableConfigurationProperties(IngestTwitterProperties::class)
class TwitterApplication {

	@Bean(IntegrationContextUtils.METADATA_STORE_BEAN_NAME)
	fun redisMetadataStore(stringRedisTemplate: StringRedisTemplate) =
			RedisMetadataStore(stringRedisTemplate)

}