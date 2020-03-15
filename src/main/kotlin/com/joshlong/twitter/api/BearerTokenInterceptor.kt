package com.joshlong.twitter.api

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference

class BearerTokenInterceptor(private val key: String, private val secret: String) : ClientHttpRequestInterceptor {

	private val accessTokenReference = AtomicReference<String>()
	private val accessTokenTemplate = RestTemplate()

	// todo figure out how rate limiting and timeouts interacts with this test.
	//      the tokens should be refreshed more frequently than the lifecycle of this application.
	private fun shouldRefreshToken() = this.accessTokenReference.get() == null

	override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
		val token = obtainToken(accessTokenTemplate, key, secret)
		request.headers.add(HttpHeaders.AUTHORIZATION, "Bearer $token")
		return execution.execute(request, body)
	}

	private fun obtainToken(restTemplate: RestTemplate, key: String, secret: String):
			String {

		if (!shouldRefreshToken()) {
			return this.accessTokenReference.get()
		}

		val httpHeaders = HttpHeaders(LinkedMultiValueMap())
				.apply {
					contentType = MediaType.APPLICATION_JSON
					val encodedCredentials = HttpHeaders.encodeBasicAuth(key, secret, Charset.defaultCharset())
					if (!containsKey(HttpHeaders.AUTHORIZATION)) {
						setBasicAuth(encodedCredentials)
					}
				}
		val uri = "https://api.twitter.com/oauth2/token?grant_type=client_credentials"
		val request: RequestEntity<Void> = RequestEntity.post(URI.create(uri)).headers(httpHeaders).build()
		val responseEntityJsonNode = restTemplate.postForEntity(uri, request, JsonNode::class.java)
		val jsonNode = responseEntityJsonNode.body!!
		val at = jsonNode["access_token"].textValue()
		this.accessTokenReference.set(at)
		return at
	}

}