package pe.gustavo.tecladolibre;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EditorRulesTest {
    @Test public void actionsRespectEditorFlags() {
        assertTrue(EditorRules.shouldPerformAction(false, false, false, true));
        assertTrue(EditorRules.shouldPerformAction(false, false, true, false));
        assertFalse(EditorRules.shouldPerformAction(false, false, false, false));
        assertTrue(EditorRules.shouldPerformAction(true, false, true, true));
        assertFalse(EditorRules.shouldPerformAction(true, false, false, false));
        assertFalse(EditorRules.shouldPerformAction(false, true, true, true));
    }

    @Test public void deletionFallbackPreservesSurrogatePairs() {
        assertEquals(0, EditorRules.utf16UnitsForLastCodePoint(null));
        assertEquals(0, EditorRules.utf16UnitsForLastCodePoint(""));
        assertEquals(1, EditorRules.utf16UnitsForLastCodePoint("á"));
        assertEquals(2, EditorRules.utf16UnitsForLastCodePoint("a\uD83D\uDE00"));
        assertEquals(1, EditorRules.utf16UnitsForLastCodePoint("\uD83D"));
        assertEquals(1, EditorRules.utf16UnitsForLastCodePoint("\uDE00"));
    }
}
