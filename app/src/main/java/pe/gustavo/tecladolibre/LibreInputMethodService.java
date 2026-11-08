package pe.gustavo.tecladolibre;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.WindowInsets;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

/** A local-only input method: text is sent directly to the focused editor. */
public class LibreInputMethodService extends InputMethodService
        implements MinimalKeyboardView.Listener {
    private static final long DOUBLE_TAP_MILLIS = 400;
    private MinimalKeyboardView keyboard;
    private EditorInfo editor;
    private boolean shifted;
    private boolean locked;
    private boolean manualShift;
    private long lastShiftTap;
    private int safeLeft, safeRight, safeBottom;
    private int baseLeft, baseTop, baseRight, baseBottom;
    private int navigationBackgroundId;
    private int imeNavigationId;
    private final Rect keyboardBounds = new Rect();
    private final Rect navigationBounds = new Rect();
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::applySafePadding;
    private ViewTreeObserver layoutObserver;

    @Override public void onCreate() {
        super.onCreate();
        navigationBackgroundId = getResources().getIdentifier("navigationBarBackground", "id", "android");
        imeNavigationId = getResources().getIdentifier("input_method_navigation_bar_view", "id", "android");
        Window window = getWindow().getWindow();
        if (window != null) {
            window.setNavigationBarColor(Color.rgb(16, 17, 18));
            window.getDecorView().setSystemUiVisibility(
                    window.getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
        }
    }

    @Override public View onCreateInputView() {
        removeLayoutListener();
        if (keyboard != null) keyboard.release();
        keyboard = new MinimalKeyboardView(this, this);
        baseLeft = keyboard.getPaddingLeft();
        baseTop = keyboard.getPaddingTop();
        baseRight = keyboard.getPaddingRight();
        baseBottom = keyboard.getPaddingBottom();
        layoutObserver = keyboard.getViewTreeObserver();
        layoutObserver.addOnGlobalLayoutListener(layoutListener);
        keyboard.setOnApplyWindowInsetsListener((view, insets) -> {
            int insetLeft;
            int insetRight;
            int insetBottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                insetLeft = safe.left;
                insetRight = safe.right;
                insetBottom = safe.bottom;
            } else {
                insetLeft = insets.getSystemWindowInsetLeft();
                insetRight = insets.getSystemWindowInsetRight();
                insetBottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    insetLeft = Math.max(insetLeft, insets.getDisplayCutout().getSafeInsetLeft());
                    insetRight = Math.max(insetRight, insets.getDisplayCutout().getSafeInsetRight());
                    insetBottom = Math.max(insetBottom, insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
            safeLeft = insetLeft;
            safeRight = insetRight;
            safeBottom = insetBottom;
            applySafePadding();
            return insets;
        });
        configureKeyboard();
        return keyboard;
    }

    private void removeLayoutListener() {
        if (layoutObserver != null && layoutObserver.isAlive()) {
            layoutObserver.removeOnGlobalLayoutListener(layoutListener);
        } else if (keyboard != null && keyboard.getViewTreeObserver().isAlive()) {
            keyboard.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        }
        layoutObserver = null;
    }

    // The IME navigation controls can overlap the input view even when the
    // window reports no navigation-bar inset (observed on HyperOS Android 16).
    private void applySafePadding() {
        if (keyboard == null || getWindow() == null || getWindow().getWindow() == null) return;
        View decor = getWindow().getWindow().getDecorView();
        View navigation = imeNavigationId == 0 ? null : decor.findViewById(imeNavigationId);
        if (navigation == null || !navigation.isShown()) {
            navigation = navigationBackgroundId == 0 ? null : decor.findViewById(navigationBackgroundId);
        }
        int overlap = 0;
        if (navigation != null && navigation.isShown() && keyboard.isShown()) {
            Rect keys = keyboardBounds;
            Rect bar = navigationBounds;
            if (keyboard.getGlobalVisibleRect(keys) && navigation.getGlobalVisibleRect(bar)
                    && bar.left < keys.right && bar.right > keys.left
                    && bar.top < keys.bottom && bar.bottom >= keys.bottom) {
                overlap = keys.bottom - Math.max(keys.top, bar.top);
            }
        }
        int bottom = baseBottom + Math.max(safeBottom, overlap);
        if (keyboard.getPaddingLeft() != baseLeft + safeLeft
                || keyboard.getPaddingTop() != baseTop
                || keyboard.getPaddingRight() != baseRight + safeRight
                || keyboard.getPaddingBottom() != bottom) {
            keyboard.setPadding(baseLeft + safeLeft, baseTop, baseRight + safeRight, bottom);
        }
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        editor = attribute;
        shifted = false;
        locked = false;
        manualShift = false;
        lastShiftTap = 0;
        configureKeyboard();
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        editor = info;
        configureKeyboard();
        if (keyboard != null) keyboard.requestApplyInsets();
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onWindowShown() {
        super.onWindowShown();
        // Android 16 lets an IME hide its own caption controls. Keep the
        // keyboard's globe picker available; do not hide global navigation.
        // If the system refuses, measured safe padding still protects the keys.
        if (Build.VERSION.SDK_INT >= 36) {
            Window window = getWindow().getWindow();
            if (window != null && window.getInsetsController() != null) {
                window.getInsetsController().hide(WindowInsets.Type.captionBar());
            }
        }
    }

    private void configureKeyboard() {
        if (keyboard == null || editor == null) return;
        int inputClass = editor.inputType & InputType.TYPE_MASK_CLASS;
        if (inputClass == InputType.TYPE_CLASS_NUMBER) {
            keyboard.setMode(MinimalKeyboardView.Mode.NUMBER);
        } else if (inputClass == InputType.TYPE_CLASS_DATETIME) {
            keyboard.setMode(MinimalKeyboardView.Mode.DATETIME);
        } else if (inputClass == InputType.TYPE_CLASS_PHONE) {
            keyboard.setMode(MinimalKeyboardView.Mode.PHONE);
        } else {
            keyboard.setMode(MinimalKeyboardView.Mode.TEXT);
        }
        keyboard.setEnterLabel(enterLabel(editor));
        updateShift();
    }

    private static String enterLabel(EditorInfo info) {
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "↵";
        if (info.actionLabel != null && info.actionLabel.length() > 0) return info.actionLabel.toString();
        switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_GO: return "Ir";
            case EditorInfo.IME_ACTION_SEARCH: return "Buscar";
            case EditorInfo.IME_ACTION_SEND: return "Enviar";
            case EditorInfo.IME_ACTION_NEXT: return "Siguiente";
            case EditorInfo.IME_ACTION_PREVIOUS: return "Anterior";
            case EditorInfo.IME_ACTION_DONE: return "Listo";
            default: return "↵";
        }
    }

    private static boolean isMultiline(EditorInfo info) {
        return (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                && (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
    }

    private boolean canAutoCapitalize() {
        if (editor == null || (editor.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        int variation = editor.inputType & InputType.TYPE_MASK_VARIATION;
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD
                && variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                && variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                && variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                && variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                && variation != InputType.TYPE_TEXT_VARIATION_URI;
    }

    private void updateShift() {
        if (!locked && !manualShift) {
            InputConnection connection = getCurrentInputConnection();
            shifted = canAutoCapitalize() && connection != null
                    && connection.getCursorCapsMode(editor.inputType) != 0;
        }
        if (keyboard != null) keyboard.setShiftState(shifted || locked, locked);
    }

    @Override public void onText(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || text == null || text.isEmpty()) return;
        if (isRawEditor()) {
            KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray());
            if (events != null) {
                for (KeyEvent event : events) connection.sendKeyEvent(event);
            } else {
                connection.commitText(text, 1);
            }
        } else {
            connection.commitText(text, 1);
        }
        if (!locked) {
            manualShift = false;
            shifted = false;
        }
        lastShiftTap = 0;
        updateShift();
    }

    @Override public void onBackspace() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        if (isRawEditor()) {
            sendKey(connection, KeyEvent.KEYCODE_DEL);
            return;
        }
        CharSequence selected = connection.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            connection.commitText("", 1);
        } else {
            // Code points, rather than UTF-16 units, keep surrogate pairs intact.
            if (!connection.deleteSurroundingTextInCodePoints(1, 0)) {
                CharSequence before = connection.getTextBeforeCursor(2, 0);
                int count = EditorRules.utf16UnitsForLastCodePoint(before);
                if (count > 0) connection.deleteSurroundingText(count, 0);
            }
        }
        lastShiftTap = 0;
        updateShift();
    }

    @Override public void onEnter() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        if (isRawEditor()) {
            sendKey(connection, KeyEvent.KEYCODE_ENTER);
            return;
        }
        int action = editor == null ? EditorInfo.IME_ACTION_UNSPECIFIED
                : editor.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean customAction = editor != null && editor.actionLabel != null && editor.actionLabel.length() > 0;
        if (editor != null && EditorRules.shouldPerformAction(isMultiline(editor),
                (editor.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0,
                customAction, action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED)) {
            connection.performEditorAction(customAction ? editor.actionId : action);
        } else {
            connection.commitText("\n", 1);
        }
        lastShiftTap = 0;
        updateShift();
    }

    private boolean isRawEditor() {
        return editor != null && editor.inputType == InputType.TYPE_NULL;
    }

    private static void sendKey(InputConnection connection, int keyCode) {
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    @Override public void onShift() {
        long now = SystemClock.uptimeMillis();
        if (locked) {
            locked = false;
            shifted = false;
            manualShift = true;
            lastShiftTap = 0;
        } else if (lastShiftTap != 0 && now - lastShiftTap <= DOUBLE_TAP_MILLIS) {
            locked = true;
            shifted = true;
            manualShift = false;
            lastShiftTap = 0;
        } else {
            shifted = !shifted;
            manualShift = true;
            lastShiftTap = now;
        }
        if (keyboard != null) keyboard.setShiftState(shifted, locked);
    }

    @Override public void onSwitchKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.showInputMethodPicker();
    }

    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart,
            int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        updateShift();
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        if (keyboard != null) keyboard.release();
        super.onFinishInputView(finishingInput);
    }

    @Override public void onFinishInput() {
        if (keyboard != null) keyboard.release();
        editor = null;
        shifted = locked = manualShift = false;
        lastShiftTap = 0;
        super.onFinishInput();
    }

    @Override public void onDestroy() {
        removeLayoutListener();
        if (keyboard != null) keyboard.release();
        keyboard = null;
        super.onDestroy();
    }
}
