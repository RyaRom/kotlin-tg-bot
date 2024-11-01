package com.demoBot.service

import com.demoBot.data.Publication
import com.demoBot.data.PublicationRepo
import com.demoBot.data.User
import com.demoBot.data.UserRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepo: UserRepo,
    private val publicationRepo: PublicationRepo,
) {
    private val mailRegex = Regex(
        "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+" +
                "(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01" +
                "-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-" +
                "\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\"" +
                ")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0" +
                "-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4" +
                "][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][" +
                "0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x0" +
                "1-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7" +
                "f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)])"
    )

    @Transactional(readOnly = true)
    suspend fun getAllUsers(): List<User> = userRepo.findAll()

    @Transactional(readOnly = true)
    suspend fun getAllPubl(): List<Publication> = publicationRepo.findAll()

    @Transactional(readOnly = true)
    suspend fun isUserExist(chatId: Long): Boolean = userRepo.existsUserByChatId(chatId)

    @Transactional(readOnly = true)
    suspend fun isUserExistWithMail(email: String): Boolean = userRepo.existsUserByEmail(email)

    @Transactional(readOnly = true)
    suspend fun getUser(chatId: Long): User? = userRepo.findByChatId(chatId)


    @Transactional
    suspend fun saveUser(chatId: Long, email: String): User {
        val user = User(chatId, email)
        return userRepo.save(user)
    }

    @Transactional
    suspend fun savePublication(chatId: Long, title: String?, content: String?): Publication {
        val author = getUser(chatId)
        val post = Publication(
            title = title ?: "empty", content = content ?: "empty", author = author
        )
        return publicationRepo.save(post)
    }

    suspend fun isMail(email: String) = email.matches(mailRegex)
}
