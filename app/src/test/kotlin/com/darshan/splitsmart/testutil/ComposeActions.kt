package com.darshan.splitsmart.testutil

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction

/** Runs the node's custom accessibility action called [label], the way TalkBack's actions menu does. */
fun SemanticsNodeInteraction.performCustomAction(label: String): Boolean {
    val actions = fetchSemanticsNode().config[SemanticsActions.CustomActions]
    return actions.single { it.label == label }.action()
}
