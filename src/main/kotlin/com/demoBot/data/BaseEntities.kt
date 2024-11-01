package com.demoBot.data

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(nullable = false)
    val name: String,

    @Column(unique = true, nullable = false)
    val email: String,
) {
    @OneToMany(mappedBy = "author", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val publications: List<Publication> = emptyList()
}

@Entity
@Table(name = "publications")
data class Publication(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    val title: String,

    val content: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val author: User? = null
)

interface UserRepo : JpaRepository<User, Long> {
    fun findByName(name: String): User?
}

interface PublicationRepo : JpaRepository<Publication, Long> {
    fun findByAuthorId(authorId: Long): List<Publication>
}