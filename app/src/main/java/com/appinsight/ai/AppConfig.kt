package com.appinsight.ai

/** Runtime configuration for the Android client. Keep provider secrets out of this file. */
object AppConfig {
    const val backendBaseUrl = "https://appinsight-backend.vercel.app"
    const val privacyPolicyUrl = "$backendBaseUrl/api/privacy"

    // Google demo IDs are safe for development. Replace them before production release.
    const val testBannerAdUnit = "ca-app-pub-3940256099942544/6300978111"
}
