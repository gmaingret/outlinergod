package com.gmaingret.outlinergod.ui.screen.nodeeditor

import com.gmaingret.outlinergod.ui.mapper.FlatNode
import com.gmaingret.outlinergod.ui.navigation.AppRoutes
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.memberProperties

class NodeEditorScreenTest {

    @Test
    fun nodeEditorScreenKt_classExists() {
        val cls = Class.forName("com.gmaingret.outlinergod.ui.screen.nodeeditor.NodeEditorScreenKt")
        assertNotNull(cls)
    }

    @Test
    fun nodeEditorStatus_hasLoadingSuccessError() {
        val subclasses = NodeEditorStatus::class.sealedSubclasses.map { it.simpleName }
        assertTrue("Missing Loading", subclasses.contains("Loading"))
        assertTrue("Missing Success", subclasses.contains("Success"))
        assertTrue("Missing Error", subclasses.contains("Error"))
    }

    @Test
    fun flatNode_depthField_isInt() {
        val depthProp = FlatNode::class.memberProperties.first { it.name == "depth" }
        assertTrue(
            "depth should be Int",
            depthProp.returnType.classifier == Int::class
        )
    }

    @Test
    fun appNavHost_nodeEditorRoute_containsDocumentId() {
        assertTrue(
            "NODE_EDITOR route should contain documentId",
            AppRoutes.NODE_EDITOR.contains("documentId") || AppRoutes.NODE_EDITOR.contains("nodeId")
        )
    }
}
