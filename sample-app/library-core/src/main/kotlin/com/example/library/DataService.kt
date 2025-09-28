package com.example.library

data class User(val id: Long, val name: String, val email: String)

class DataService {
    fun getUser(id: Long): User {
        Logger.info("Fetching user with id: $id")
        return User(id, "Sample User", "user@example.com")
    }

    fun saveUser(user: User): Boolean {
        Logger.info("Saving user: ${user.name}")
        // Simulate save operation
        return true
    }
}