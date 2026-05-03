package com.novarehab.media_messaging.ui

object MediaInboxBadge {
    fun format(unseenCount: Int): String {
        return if (unseenCount > 0) {
            "🖼\nGALERIJA\n$unseenCount nova"
        } else {
            "🖼\nGALERIJA"
        }
    }
}
