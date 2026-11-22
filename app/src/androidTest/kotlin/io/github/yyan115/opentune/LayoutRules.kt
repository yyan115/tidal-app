package io.github.yyan115.opentune

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * Rules that every screen keeps in every state and on every phone setting. They read what is on the screen and know nothing of which screen it is,
 * so a screen that is added later is held to them as well.
 */
object LayoutRules {
    private fun placed(node: SemanticsNode) = node.layoutInfo.isPlaced && !node.config.contains(SemanticsProperties.InvisibleToUser) && !node.boundsInRoot.isEmpty && Readability.insideItsParents(node)

    private fun textOf(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    private fun scrollsSideways(node: SemanticsNode) = generateSequence(node.parent) { it.parent }.any { it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) }

    private fun overlap(a: Rect, b: Rect): Float { val i = a.intersect(b); return if (i.isEmpty) 0f else i.width * i.height }

    /** What is wrong on the screen now: overlapping texts, texts cut by the edge of the screen, controls on top of each other and controls with no name. */
    fun problems(compose: ComposeTestRule): List<String> {
        compose.waitForIdle()
        val found = mutableListOf<String>()
        val unmerged = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        for (root in unmerged) {
            val texts = Readability.texts(root).filter { placed(it) && !Readability.isDisabled(it) && textOf(it).isNotBlank() }
            for (i in texts.indices) for (j in i + 1 until texts.size) {
                val a = texts[i]; val b = texts[j]
                val shared = overlap(a.boundsInRoot, b.boundsInRoot)
                val smaller = minOf(a.boundsInRoot.width * a.boundsInRoot.height, b.boundsInRoot.width * b.boundsInRoot.height)
                if (smaller > 0f && shared > 0.25f * smaller) found += "overlap: \"${textOf(a).take(30)}\" and \"${textOf(b).take(30)}\""
            }
            val screen = root.boundsInRoot
            // A text that is cut by the edge of the screen has its centre inside or outside of it, so only whether it touches the screen counts.
            for (text in Readability.texts(root).filter { it.layoutInfo.isPlaced && !it.boundsInRoot.isEmpty && textOf(it).isNotBlank() && !scrollsSideways(it) }) {
                val b = text.boundsInRoot
                if (overlap(b, screen) > 0f && (b.left < screen.left - 1f || b.right > screen.right + 1f)) found += "outside the screen: \"${textOf(text).take(30)}\" at $b on a screen $screen"
            }
        }
        // The controls are read in the merged tree, where a button holds the text and the description of what is in it.
        for (root in compose.onAllNodes(isRoot()).fetchSemanticsNodes()) {
            val controls = mutableListOf<SemanticsNode>()
            fun walk(node: SemanticsNode) {
                if (node.config.contains(SemanticsActions.OnClick) && placed(node) && !Readability.isDisabled(node)) controls += node
                node.children.forEach(::walk)
            }
            walk(root)
            for (control in controls) if (describe(control).isBlank()) found += "a control with no name at ${control.boundsInRoot}"
            // Two controls that lie on each other are hard to hit, and the finger cannot tell which one it chose.
            for (i in controls.indices) for (j in i + 1 until controls.size) {
                val a = controls[i]; val b = controls[j]
                val ancestor = generateSequence(a.parent) { it.parent }.any { it.id == b.id } || generateSequence(b.parent) { it.parent }.any { it.id == a.id }
                val shared = overlap(a.boundsInRoot, b.boundsInRoot)
                val smaller = minOf(a.boundsInRoot.width * a.boundsInRoot.height, b.boundsInRoot.width * b.boundsInRoot.height)
                if (!ancestor && smaller > 0f && shared > 0.5f * smaller) found += "controls on top of each other: \"${describe(a).take(25)}\" and \"${describe(b).take(25)}\""
            }
        }
        return found.distinct()
    }

    private fun describe(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty().ifBlank { textOf(node) }
}
