package com.demoBot.config

import com.demoBot.service.DemoBot
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@Configuration
class BotConfig(private val demoBot: DemoBot) {

    @Bean
    fun botInit(): TelegramBotsApi {
        val api = TelegramBotsApi(DefaultBotSession()::class.java)
        api.registerBot(demoBot)
        return api
    }
}