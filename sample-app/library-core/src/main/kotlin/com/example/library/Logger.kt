package com.example.library

class Logger {
    companion object {
        fun info(message: String) {
            println("[INFO] $message")
        }

        fun error(message: String, throwable: Throwable? = null) {
            println("[ERROR] $message")
            throwable?.printStackTrace()
        }
    }
}