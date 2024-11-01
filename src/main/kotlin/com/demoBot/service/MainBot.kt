package com.demoBot.service

import com.demoBot.data.Publication
import com.demoBot.data.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import kotlin.coroutines.CoroutineContext

@Service
class DemoBot(
    private val userService: UserService,
) : TelegramLongPollingBot(), CoroutineScope {
    @Value("\${telegram.bot.token}")
    private lateinit var botToken: String

    @Value("\${telegram.bot.username}")
    private lateinit var botUsername: String

    @Value("\${telegram.template.command-list}")
    private lateinit var commands: String

    @Value("\${telegram.template.publ-command-list}")
    private lateinit var publCommands: String

    @Value("\${telegram.template.test-command-list}")
    private lateinit var testCommands: String

    override fun getBotToken() = botToken

    override fun getBotUsername() = botUsername

    val userStates = HashMap<Long, UserState?>()
    val pendingPublications = HashMap<Long, HashMap<String, String>>()

    override fun onUpdateReceived(update: Update) {
        if (!update.hasMessage() || !update.message.hasText()) return

        launch {
            val chatId = update.message.chatId
            val text = update.message.text

            println("State = ${userStates[chatId]} Text = $text ChatId = $chatId")

            when (userStates[chatId]) {
                null -> handleStartCommand(chatId, text)
                UserState.REGISTRATION -> handleReg(chatId, text)
                UserState.AWAITING_TITLE -> handleTitle(chatId, text)
                UserState.AWAITING_CONTENT -> handleContent(chatId, text)
                UserState.AWAITING_PUBLICATION -> handlePublActions(chatId, text)
                UserState.PASSIVE -> handleActions(chatId, text)
                UserState.TESTING -> handleTestActions(chatId, text)
            }
        }
    }

    private suspend fun handleStartCommand(chatId: Long, text: String) {
        when (text) {
            "/start" -> {
                if (userService.isUserExist(chatId)) {
                    switchToPassive(chatId)
                    return
                }
                sendMessage(chatId, "Welcome! Please enter your email to register:")
                userStates[chatId] = UserState.REGISTRATION
            }

            "/test" -> {
                sendMessage(chatId, "entering test mode")
                sendMessage(chatId, testCommands)
                userStates[chatId] = UserState.TESTING
            }

            else -> {
                sendMessage(chatId, "Please type /start to register.")
            }
        }
    }

    private suspend fun handleReg(chatId: Long, email: String) {
        if (!userService.isMail(email)) {
            sendMessage(chatId, "Write actual email")
            return
        }
        if (userService.isUserExistWithMail(email)) {
            sendMessage(chatId, "Email is already taken")
            return
        }
        userService.saveUser(chatId, email)
        switchToPassive(chatId)
    }


    private suspend fun handleActions(chatId: Long, text: String) {
        when (text) {
            "/new_publication" -> handlePublicationCreation(chatId)
            "/get_all_users" -> sendMessage(chatId, getUserStr())
            "/get_all_publications" -> sendMessage(chatId, getPublStr())
            else -> {
                sendMessage(chatId, commands)
            }
        }
    }

    private suspend fun getUserStr() = userService.getAllUsers()
        .joinToString(separator = "\n") {
            "User ${it.email}"
        }

    private suspend fun getPublStr() = userService.getAllPubl()
        .joinToString(separator = "\n\n") {
            "Author = ${it.author?.email ?: "no mail"}\n" +
                    "${it.title}\n" + it.content
        }

    private suspend fun handleTestActions(chatId: Long, text: String) {
        when (text) {
            "/get_back" -> {
                userStates[chatId] = null
                return
            }

            else -> {
                sendMessage(chatId, testCommands)
            }
        }
    }

    private suspend fun handlePublActions(chatId: Long, text: String) {
        when (text) {
            "/post" -> onPostPublication(chatId)
            "/cancel" -> onCancelPublication(chatId)
            "/rework" -> onReworkPublication(chatId)
            else -> {
                sendMessage(chatId, publCommands)
            }
        }
    }

    private suspend fun onCancelPublication(chatId: Long) {
        pendingPublications.remove(chatId)
        sendMessage(chatId, "Publication cancelled")
        switchToPassive(chatId)
    }

    private suspend fun onReworkPublication(chatId: Long) {
        pendingPublications[chatId]?.remove("content")
        sendMessage(chatId, "Waiting for new text")
        userStates[chatId] = UserState.AWAITING_CONTENT
    }

    private suspend fun onPostPublication(chatId: Long) {
        val post = userService.savePublication(
            chatId,
            pendingPublications[chatId]?.get("title"),
            pendingPublications[chatId]?.get("content")
        )
        switchToPassive(chatId)
        notifyAll(publication = post, currentChatId = chatId)
    }

    private suspend fun handleTitle(chatId: Long, text: String) {
        pendingPublications.putIfAbsent(chatId, HashMap())
        pendingPublications[chatId]!!["title"] = text
        sendMessage(chatId, "Great! Now, send the content of your publication.")
        userStates[chatId] = UserState.AWAITING_CONTENT
    }

    private suspend fun handleContent(chatId: Long, text: String) {
        pendingPublications.putIfAbsent(chatId, HashMap())
        pendingPublications[chatId]!!["content"] = text
        sendMessage(chatId, "Great! Publication saved.")
        sendMessage(chatId, publCommands)
        userStates[chatId] = UserState.AWAITING_PUBLICATION
    }

    private suspend fun handlePublicationCreation(chatId: Long) {
        sendMessage(chatId, "Let's create a new publication! Please send the title of your publication.")
        userStates[chatId] = UserState.AWAITING_TITLE
    }

    private suspend fun sendMessage(chatId: Long, text: String) {
        coroutineScope {
            launch {
                execute(SendMessage(chatId.toString(), text))
            }
        }

    }

    private suspend fun switchToPassive(chatId: Long) {
        userStates[chatId] = UserState.PASSIVE
        sendMessage(chatId, commands)
    }

    private suspend fun notifyAll(publication: Publication, currentChatId: Long) {
        val messageText = "New publication by ${publication.author?.email}:\n" +
                "Title: ${publication.title}\n" +
                "Content: ${publication.content}"

        val users = userService.getAllUsers()
        coroutineScope {
            launch {
                users.map { user ->
                    async {
                        notification(user, messageText, currentChatId)
                    }
                }.awaitAll()
            }
        }
    }

    suspend fun notification(user: User, message: String, currentChatId: Long) {
        if (user.chatId == currentChatId) return
        println("Send to ${user.email}")
        sendMessage(chatId = user.chatId, text = message)
    }

    private val processJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + processJob
}

enum class UserState {
    REGISTRATION,
    AWAITING_TITLE,
    AWAITING_CONTENT,
    AWAITING_PUBLICATION,
    PASSIVE,
    TESTING
}
