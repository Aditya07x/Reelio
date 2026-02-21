package com.example.instatracker

import android.view.accessibility.AccessibilityEvent

enum class InteractionType {
    LIKE,
    COMMENT,
    SHARE
}

object InteractionDetector {

    fun detectInteraction(event: AccessibilityEvent): InteractionType? {
        val source = event.source ?: return null
        try {
            val viewId = source.viewIdResourceName.orEmpty().lowercase()
            val className = source.className?.toString()?.lowercase().orEmpty()
            val contentDesc = source.contentDescription?.toString()?.lowercase().orEmpty()
            val text = event.text?.joinToString(" ")?.lowercase().orEmpty()
            val combinedLabel = "$contentDesc $text"

            if (isLike(viewId, className, combinedLabel)) {
                return InteractionType.LIKE
            }
            if (isComment(viewId, className, combinedLabel)) {
                return InteractionType.COMMENT
            }
            if (isShare(viewId, className, combinedLabel)) {
                return InteractionType.SHARE
            }

            return null
        } finally {
            source.recycle()
        }
    }

    private fun isLike(viewId: String, className: String, label: String): Boolean {
        if ("like" in viewId || "like_button" in viewId || "heart" in viewId) return true
        if ("like" in label || "curtir" in label || "me gusta" in label) return true
        if ("imagebutton" in className && ("like" in label || "heart" in label)) return true
        return false
    }

    private fun isComment(viewId: String, className: String, label: String): Boolean {
        if ("comment" in viewId || "comment_button" in viewId) return true
        if ("comment" in label || "coment" in label || "comentar" in label) return true
        if ("imagebutton" in className && "comment" in label) return true
        return false
    }

    private fun isShare(viewId: String, className: String, label: String): Boolean {
        if ("share" in viewId || "send" in viewId) return true
        if ("share" in label || "send" in label || "compart" in label || "enviar" in label) return true
        if ("imagebutton" in className && ("share" in label || "send" in label)) return true
        return false
    }
}

