package com.example.data

data class UserData(
    val id: Long,
    val name: String,
    val email: String,
    val createdAt: Long = System.currentTimeMillis()
)

interface UserRepository {
    fun save(user: UserData): Boolean
    fun findById(id: Long): UserData?
    fun findAll(): List<UserData>
    fun delete(id: Long): Boolean
}

class InMemoryUserRepository : UserRepository {
    private val users = mutableMapOf<Long, UserData>()

    override fun save(user: UserData): Boolean {
        users[user.id] = user
        return true
    }

    override fun findById(id: Long): UserData? {
        return users[id]
    }

    override fun findAll(): List<UserData> {
        return users.values.toList()
    }

    override fun delete(id: Long): Boolean {
        return users.remove(id) != null
    }
}