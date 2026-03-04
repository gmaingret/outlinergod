package com.gmaingret.outlinergod.search

import org.junit.Assert.*
import org.junit.Test

class SearchQueryParserTest {

    @Test
    fun plainText_setsFtsTermsWithPrefixStar_andNoFilters() {
        val result = SearchQueryParser.parse("apple pie")
        assertEquals("apple pie*", result.ftsTerms)
        assertNull(result.isCompleted)
        assertNull(result.color)
        assertFalse(result.inNote)
        assertFalse(result.inTitle)
    }

    @Test
    fun isCompleted_operator_setsCompletedTrue_andRemainsAsTerms() {
        val result = SearchQueryParser.parse("apple is:completed")
        assertEquals("apple*", result.ftsTerms)
        assertEquals(true, result.isCompleted)
        assertNull(result.color)
    }

    @Test
    fun isNotCompleted_operator_setsCompletedFalse() {
        val result = SearchQueryParser.parse("is:not-completed work")
        assertEquals("work*", result.ftsTerms)
        assertEquals(false, result.isCompleted)
    }

    @Test
    fun colorRed_operator_setsColor1() {
        val result = SearchQueryParser.parse("color:red meeting")
        assertEquals("meeting*", result.ftsTerms)
        assertEquals(1, result.color)
        assertNull(result.isCompleted)
    }

    @Test
    fun colorOrange_operator_setsColor2() {
        val result = SearchQueryParser.parse("color:orange")
        assertEquals("", result.ftsTerms)
        assertEquals(2, result.color)
    }

    @Test
    fun colorYellow_operator_setsColor3() {
        val result = SearchQueryParser.parse("color:yellow")
        assertEquals("", result.ftsTerms)
        assertEquals(3, result.color)
    }

    @Test
    fun colorGreen_operator_setsColor4() {
        val result = SearchQueryParser.parse("color:green")
        assertEquals("", result.ftsTerms)
        assertEquals(4, result.color)
    }

    @Test
    fun colorBlue_operator_setsColor5() {
        val result = SearchQueryParser.parse("color:blue")
        assertEquals("", result.ftsTerms)
        assertEquals(5, result.color)
    }

    @Test
    fun colorPurple_operator_setsColor6() {
        val result = SearchQueryParser.parse("color:purple")
        assertEquals("", result.ftsTerms)
        assertEquals(6, result.color)
    }

    @Test
    fun inNote_operator_setsInNoteTrue_inTitleFalse() {
        val result = SearchQueryParser.parse("in:note todo")
        assertEquals("todo*", result.ftsTerms)
        assertTrue(result.inNote)
        assertFalse(result.inTitle)
    }

    @Test
    fun inTitle_operator_setsInTitleTrue_inNoteFalse() {
        val result = SearchQueryParser.parse("in:title project")
        assertEquals("project*", result.ftsTerms)
        assertFalse(result.inNote)
        assertTrue(result.inTitle)
    }

    @Test
    fun emptyString_returnsEmptyFtsTerms_andAllNullOrFalse() {
        val result = SearchQueryParser.parse("")
        assertEquals("", result.ftsTerms)
        assertNull(result.isCompleted)
        assertNull(result.color)
        assertFalse(result.inNote)
        assertFalse(result.inTitle)
    }

    @Test
    fun multipleOperators_allExtractedCorrectly() {
        val result = SearchQueryParser.parse("in:note is:completed color:green meeting")
        assertEquals("meeting*", result.ftsTerms)
        assertEquals(true, result.isCompleted)
        assertEquals(4, result.color)
        assertTrue(result.inNote)
        assertFalse(result.inTitle)
    }

    @Test
    fun termAlreadyEndingWithStar_notDoubled() {
        val result = SearchQueryParser.parse("appl*")
        assertEquals("appl*", result.ftsTerms)
    }

    @Test
    fun operatorOnly_noRemainingTerms_emptyFtsTerms() {
        val result = SearchQueryParser.parse("is:completed")
        assertEquals("", result.ftsTerms)
        assertEquals(true, result.isCompleted)
    }

    @Test
    fun caseInsensitiveOperators_parsed() {
        val result = SearchQueryParser.parse("IS:COMPLETED COLOR:RED")
        assertEquals(true, result.isCompleted)
        assertEquals(1, result.color)
        assertEquals("", result.ftsTerms)
    }
}
