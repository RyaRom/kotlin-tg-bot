package com.demoBot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoBotApplication

fun main(args: Array<String>) {
	runApplication<DemoBotApplication>(*args)
}
