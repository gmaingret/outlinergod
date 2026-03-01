package com.outlinegod.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Standalone prototype Activity for the WYSIWYG formatting proof-of-concept.
 *
 * Uses [BasicTextField] with the [TextFieldValue] overload (which supports
 * [VisualTransformation]) and [MarkdownVisualTransformation] so that:
 *   - Bold, italic, code, strikethrough, and highlight are styled live.
 *   - Markers remain visible — Phase 1 scope. [OffsetMapping.Identity] ensures
 *     the cursor is always correct.
 *   - Phase 2 marker-hiding (via OutputTransformation) is explicitly deferred.
 *
 * This Activity is NOT integrated into the main app navigation — it is a
 * self-contained throwaway prototype only.
 */
class WysiwygPrototypeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "WYSIWYG Prototype — Phase 1 (visible markers)",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )

                        var value by remember {
                            mutableStateOf(
                                TextFieldValue(
                                    "Type **bold**, _italic_, `code`, ~~strike~~, or ==highlight== here."
                                )
                            )
                        }

                        BasicTextField(
                            value = value,
                            onValueChange = { value = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            visualTransformation = MarkdownVisualTransformation,
                        )

                        Text(
                            text = "Supported: **bold** _italic_ `code` ~~strike~~ ==highlight==",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
