package com.joshlong.twitter


import org.springframework.boot.builder.SpringApplicationBuilder


fun main(args: Array<String>) {
	SpringApplicationBuilder()
			.sources(TwitterApplication::class.java)
			.run(*args)
}


