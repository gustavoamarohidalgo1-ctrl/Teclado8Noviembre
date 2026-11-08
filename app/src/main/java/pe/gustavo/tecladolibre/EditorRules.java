package pe.gustavo.tecladolibre;

/** Editor decisions kept independent of Android so they can be tested locally. */
final class EditorRules {
    private EditorRules() { }

    static boolean shouldPerformAction(boolean multiline, boolean noEnterAction,
            boolean customAction, boolean standardAction) {
        return !noEnterAction && (customAction || standardAction);
    }

    static int utf16UnitsForLastCodePoint(CharSequence beforeCursor) {
        if (beforeCursor == null || beforeCursor.length() == 0) return 0;
        int length = beforeCursor.length();
        return length >= 2 && Character.isSurrogatePair(beforeCursor.charAt(length - 2),
                beforeCursor.charAt(length - 1)) ? 2 : 1;
    }
}
