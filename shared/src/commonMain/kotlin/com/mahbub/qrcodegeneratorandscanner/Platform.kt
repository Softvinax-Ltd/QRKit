package com.mahbub.qrcodegeneratorandscanner

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform