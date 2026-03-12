package com.example.instatracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionDetectorTest {

    @Test
    fun commentSubmitIsCommentNotShare() {
        val match = InteractionDetector.classifyClick(
            InteractionSnapshot(
                viewIds = setOf("com.instagram.android:id/send_comment_button"),
                labels = setOf("Send"),
                classes = setOf("android.widget.imagebutton")
            )
        )

        assertEquals(InteractionType.COMMENT, match.type)
        assertTrue(match.isCommentSubmit)
    }

    @Test
    fun directShareButtonClassifiesAsShare() {
        val match = InteractionDetector.classifyClick(
            InteractionSnapshot(
                viewIds = setOf("com.instagram.android:id/direct_share_button"),
                labels = setOf("Send to"),
                classes = setOf("android.widget.imagebutton")
            )
        )

        assertEquals(InteractionType.SHARE, match.type)
    }

    @Test
    fun ancestorLikeLabelStillClassifiesLike() {
        val match = InteractionDetector.classifyClick(
            InteractionSnapshot(
                viewIds = emptySet(),
                labels = setOf("Like"),
                classes = setOf("android.widget.imageview", "android.widget.imagebutton")
            )
        )

        assertEquals(InteractionType.LIKE, match.type)
    }

    @Test
    fun savedStateChangeClassifiesSave() {
        val action = InteractionDetector.classifyStateChange(
            InteractionSnapshot(
                viewIds = setOf("com.instagram.android:id/save_button"),
                labels = setOf("Saved"),
                classes = setOf("android.widget.imagebutton"),
                selected = true
            )
        )

        assertEquals(InteractionType.SAVE, action)
    }
}