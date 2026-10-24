package com.mahbub.qrcodegeneratorandscanner

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return sayHello(platform.name)
    }
}