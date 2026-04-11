package com.jarvis.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class JarvisCoreApplication

fun main(args: Array<String>) {
    runApplication<JarvisCoreApplication>(*args)
}
