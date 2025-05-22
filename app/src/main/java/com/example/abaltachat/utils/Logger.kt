package com.example.abaltachat.utils

object Log {
    fun d(tag: String, message: String) {
        println("DEBUG: [$tag] $message")
    }
    fun e(tag: String, message: String, ex: Exception? = null) {
        println("FATAL: [$tag] $message $ex")
    }
}