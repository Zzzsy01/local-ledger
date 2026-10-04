package com.localledger.app.domain

data class AppUpdate(val versionCode: Long, val versionName: String, val apkUrl: String, val sha256: String, val notes: String, val minSdk: Int)
