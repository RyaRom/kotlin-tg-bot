package com.demoBot.service

import com.demoBot.data.Publication
import com.demoBot.data.PublicationRepo
import com.demoBot.data.User
import com.demoBot.data.UserRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Service
class DemoBot(
    private val userRepo: UserRepo,
    private val publicationRepo: PublicationRepo,
) : TelegramLongPollingBot() {
    @Value("\${telegram.bot.token}")
    private lateinit var botToken: String

    @Value("\${telegram.bot.username}")
    private lateinit var botUsername: String

    @Value("\${telegram.template.command-list}")
    private lateinit var commands: String

    @Value("\${telegram.template.publ-command-list}")
    private lateinit var publCommands: String

    override fun getBotToken() = botToken

    override fun getBotUsername() = botUsername

    val userStates = HashMap<Long, UserState?>()
    val pendingPublications = HashMap<Long, HashMap<String, String>>()

    override fun onUpdateReceived(update: Update) {
        if (!update.hasMessage() || !update.message.hasText()) return
        val chatId = update.message.chatId
        val text = update.message.text

        println("State = ${userStates[chatId]} Text = $text")

        when (userStates[chatId]) {
            null -> handleStartCommand(chatId, text)
            UserState.REGISTRATION -> handleReg(chatId, text)
            UserState.AWAITING_TITLE -> handleTitle(chatId, text)
            UserState.AWAITING_CONTENT -> handleContent(chatId, text)
            UserState.AWAITING_PUBLICATION -> handlePublActions(chatId, text)
            UserState.PASSIVE -> handleActions(chatId, text)
        }
    }

    @Transactional(readOnly = true)
    internal fun handleStartCommand(chatId: Long, text: String) {
        if (text == "/start") {
            if (userRepo.existsUserByChatId(chatId)) {
                switchToPassive(chatId)
                return
            }
            sendMessage(chatId, "Welcome! Please enter your email to register:")
            userStates[chatId] = UserState.REGISTRATION
        } else {
            sendMessage(chatId, "Please type /start to register.")
        }
    }

    @Transactional(readOnly = true)
    internal fun onGetAllUsers(chatId: Long) {
        val users = userRepo.findAll()
            .joinToString(separator = "\n") { "User ${it.email}" }

        if (users.isEmpty()) return
        sendMessage(chatId, users)
    }

    @Transactional(readOnly = true)
    internal fun onGetAllPubl(chatId: Long) {
        val posts = publicationRepo.findAll().joinToString(separator = "\n\n") {
            "Author = ${it.author?.email ?: "no mail"}\n" +
                    "${it.title}\n" + it.content
        }

        if (posts.isEmpty()) return
        sendMessage(chatId, posts)
    }

    @Transactional
    internal fun handleReg(chatId: Long, mail: String) {
        if (!mail.matches(Regex("(?:[a-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#\$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])"))) {
            sendMessage(chatId, "Write actual email")
            return
        }
        if (userRepo.existsUserByEmail(mail)) {
            sendMessage(chatId, "Email is already taken")
            return
        }
        val user = User(chatId, mail)
        userRepo.save(user)
        switchToPassive(chatId)
    }

    private fun handleActions(chatId: Long, text: String) {
        when (text) {
            "/new_publication" -> handlePublicationCreation(chatId)
            "/get_all_users" -> onGetAllUsers(chatId)
            "/get_all_publications" -> onGetAllPubl(chatId)
            else -> {
                sendMessage(chatId, commands)
            }
        }
    }

    private fun handlePublActions(chatId: Long, text: String) {
        when (text) {
            "/post" -> onPostPublication(chatId)
            "/cancel" -> onCancelPublication(chatId)
            "/rework" -> onReworkPublication(chatId)
            else -> {
                sendMessage(chatId, publCommands)
            }
        }
    }

    private fun onCancelPublication(chatId: Long) {
        pendingPublications.remove(chatId)
        sendMessage(chatId, "Publication cancelled")
        switchToPassive(chatId)
    }

    private fun onReworkPublication(chatId: Long) {
        pendingPublications[chatId]?.remove("content")
        sendMessage(chatId, "Waiting for new text")
        userStates[chatId] = UserState.AWAITING_CONTENT
    }

    @Transactional
    internal fun onPostPublication(chatId: Long) {
        val title = pendingPublications[chatId]?.get("title") ?: "empty"
        val content = pendingPublications[chatId]?.get("content") ?: "empty"
        val author = userRepo.findByChatId(chatId)
        if (author == null) {
            userStates[chatId] = null
            return
        }

        val post = Publication(
            title = title, content = content, author = author
        )
        publicationRepo.save(post)
        switchToPassive(chatId)
        notifyAll(publication = post, currentChatId = chatId)
    }

    private fun handleTitle(chatId: Long, text: String) {
        pendingPublications.putIfAbsent(chatId, HashMap())
        pendingPublications[chatId]!!["title"] = text
        sendMessage(chatId, "Great! Now, send the content of your publication.")
        userStates[chatId] = UserState.AWAITING_CONTENT
    }

    private fun handleContent(chatId: Long, text: String) {
        pendingPublications.putIfAbsent(chatId, HashMap())
        pendingPublications[chatId]!!["content"] = text
        sendMessage(chatId, "Great! Publication saved.")
        sendMessage(chatId, publCommands)
        userStates[chatId] = UserState.AWAITING_PUBLICATION
    }

    private fun handlePublicationCreation(chatId: Long) {
        sendMessage(chatId, "Let's create a new publication! Please send the title of your publication.")
        userStates[chatId] = UserState.AWAITING_TITLE
    }

    private fun sendMessage(chatId: Long, text: String) {
        execute(SendMessage(chatId.toString(), text))
    }

    private fun switchToPassive(chatId: Long) {
        userStates[chatId] = UserState.PASSIVE
        sendMessage(chatId, commands)
    }

    private fun notifyAll(publication: Publication, currentChatId: Long) {
        val messageText = "New publication by ${publication.author?.email}:\n" +
                "Title: ${publication.title}\n" +
                "Content: ${publication.content}"

        val users = userRepo.findAll()
        CoroutineScope(Dispatchers.IO).launch {
            users.map { user ->
                async {
                    notification(user, messageText, currentChatId)
                }
            }.awaitAll()
        }
    }

    suspend fun notification(user: User, message: String, currentChatId: Long) {
        if (user.chatId == currentChatId) return
        sendMessage(chatId = user.chatId, text = message)
    }
}

enum class UserState {
    REGISTRATION,
    AWAITING_TITLE,
    AWAITING_CONTENT,
    AWAITING_PUBLICATION,
    PASSIVE
}
