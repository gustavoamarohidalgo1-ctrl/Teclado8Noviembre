package pe.gustavo.tecladolibre;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Locale;

/** A small, self-contained keyboard surface. It never stores or logs committed text. */
@android.annotation.SuppressLint("ViewConstructor") // Created only by the IME with its required listener, never inflated from XML.
public class MinimalKeyboardView extends View {
    public interface Listener {
        void onText(String text);
        void onBackspace();
        void onEnter();
        void onShift();
        void onSwitchKeyboard();
    }
    public enum Mode { TEXT, NUMBER, PHONE, DATETIME }

    private static final int TEXT = 0, SHIFT = 1, DELETE = 2, ENTER = 3,
            SWITCH = 4, SYMBOLS = 5, SPACE = 6, MORE_SYMBOLS = 7;
    private static final int BACKGROUND = Color.rgb(16, 17, 18);
    private static final int KEY = Color.rgb(40, 40, 40);
    private static final int SPECIAL = Color.rgb(32, 32, 32);
    private static final int PRESSED = Color.rgb(86, 86, 86);
    private static final int ACTION = Color.rgb(80, 80, 80);
    private static final int FOREGROUND = Color.rgb(248, 248, 248);
    private static final String[][] LETTERS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"},
            {"⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"},
            {"?123", "🌐", ",", " ", ".", "↵"}
    };
    private static final String[][] SYMBOL_ROWS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/"},
            {"=", "*", "\"", "'", ":", ";", "!", "?", "⌫"},
            {"ABC", "🌐", "#+=", " ", ",", ".", "↵"}
    };
    private static final String[][] MORE_SYMBOL_ROWS = {
            {"[", "]", "{", "}", "<", ">", "\\", "|", "^", "~"},
            {"_", "`", "€", "£", "¥", "•", "×", "÷", "°", "©"},
            {"=", "*", "\"", "'", ":", ";", "!", "?", "⌫"},
            {"ABC", "🌐", "123", " ", ",", ".", "↵"}
    };
    private static final String[][] DATETIME_ROWS = {
            {"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"},
            {"/", "0", ":"}, {"🌐", "-", ".", " ", "⌫", "↵"}
    };
    private static final String[][] NUMBER_ROWS = {
            {"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"},
            {"-", "0", "."}, {"🌐", "+", "⌫", "↵"}
    };
    private static final String[][] PHONE_ROWS = {
            {"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"},
            {"*", "0", "#"}, {"🌐", "+", "⌫", "↵"}
    };

    private static final class Key {
        final RectF bounds = new RectF();
        final String value;
        final String uppercase;
        final int type;
        Key(String value, int type) { this.value = value; this.uppercase = value.toUpperCase(Locale.ROOT); this.type = type; }
    }

    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Key> keys = new ArrayList<>();
    private final float density;
    private final int longPressDelay;
    private Mode mode = Mode.TEXT;
    private boolean symbols, moreSymbols, shifted, locked, popup;
    private String enterLabel = "↵";
    private Key active;
    private int pointerId = MotionEvent.INVALID_POINTER_ID;
    private String[] alternatives;
    private int selectedAlternative = -1;
    private float popupLeft, popupTop, popupCellWidth, popupHeight;
    private float downX, downY;
    private boolean moved;
    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (active == null || moved) return;
            if (active.type == DELETE) {
                listener.onBackspace();
                deleteRepeated = true;
                handler.postDelayed(repeatDelete, 65);
            } else {
                String options = alternativesFor(active.value);
                if (active.type == TEXT && options != null) showAlternatives(options);
            }
        }
    };
    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            if (active != null && active.type == DELETE && !moved) {
                listener.onBackspace();
                handler.postDelayed(this, 65);
            }
        }
    };
    private boolean deleteRepeated;

    public MinimalKeyboardView(Context context, Listener listener) {
        super(context);
        if (listener == null) throw new IllegalArgumentException("listener is required");
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        longPressDelay = ViewConfiguration.getLongPressTimeout();
        setFocusable(false);
        setSoundEffectsEnabled(false);
        setHapticFeedbackEnabled(false);
        setBackgroundColor(BACKGROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Teclado");
    }

    public void setShiftState(boolean shifted, boolean locked) {
        this.shifted = shifted;
        this.locked = locked;
        invalidate();
    }
    public void setMode(Mode mode) {
        Mode next = mode == null ? Mode.TEXT : mode;
        if (this.mode != next) {
            cancelTouch();
            this.mode = next;
            symbols = false;
            moreSymbols = false;
            rebuildKeys();
            requestLayout();
            invalidate();
        }
    }
    public void setEnterLabel(String label) {
        enterLabel = label == null || label.trim().isEmpty() ? "↵" : label;
        invalidate();
    }
    public void refresh() { rebuildKeys(); invalidate(); }
    public void release() { cancelTouch(); handler.removeCallbacksAndMessages(null); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean landscape = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int wantedHeight = (int) ((landscape ? 180 : mode == Mode.TEXT ? 238 : 260) * density + .5f);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(wantedHeight + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cancelTouch();
        rebuildKeys();
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Insets can change padding without changing the outer view size.
        cancelTouch();
        rebuildKeys();
    }
    private void rebuildKeys() {
        keys.clear();
        if (getWidth() <= 0 || getHeight() <= 0) return;
        String[][] rows = mode == Mode.NUMBER ? NUMBER_ROWS : mode == Mode.PHONE ? PHONE_ROWS
                : mode == Mode.DATETIME ? DATETIME_ROWS
                : symbols ? (moreSymbols ? MORE_SYMBOL_ROWS : SYMBOL_ROWS) : LETTERS;
        float contentWidth = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        float contentHeight = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
        float rowHeight = contentHeight / rows.length;
        float gap = Math.min(3 * density, rowHeight * .055f);
        for (int row = 0; row < rows.length; row++) {
            float total = 0;
            for (String value : rows[row]) total += weight(value, row, rows.length);
            float x = getPaddingLeft();
            for (String value : rows[row]) {
                float width = contentWidth * weight(value, row, rows.length) / total;
                Key key = new Key(value, typeOf(value));
                key.bounds.set(x + gap, getPaddingTop() + row * rowHeight + gap,
                        x + width - gap, getPaddingTop() + (row + 1) * rowHeight - gap);
                keys.add(key);
                x += width;
            }
        }
    }
    private float weight(String value, int row, int rowCount) {
        if (mode != Mode.TEXT) return 1;
        if (" ".equals(value)) return 4.1f;
        if ("?123".equals(value) || "ABC".equals(value)) return 1.35f;
        if ("↵".equals(value)) return 1.5f;
        if ("⇧".equals(value) || "⌫".equals(value)) return 1.35f;
        return 1;
    }
    private static int typeOf(String value) {
        switch (value) {
            case "⇧": return SHIFT;
            case "⌫": return DELETE;
            case "↵": return ENTER;
            case "🌐": return SWITCH;
            case "?123": case "ABC": return SYMBOLS;
            case "#+=": case "123": return MORE_SYMBOLS;
            case " ": return SPACE;
            default: return TEXT;
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            int color = key == active && !popup ? PRESSED : key.type == ENTER ? ACTION
                    : key.type == TEXT || key.type == SPACE ? KEY : SPECIAL;
            if (key.type == SHIFT && (shifted || locked)) color = ACTION;
            paint.setColor(color);
            canvas.drawRoundRect(key.bounds, 5 * density, 5 * density, paint);
            if (key.type == SWITCH) {
                drawGlobe(canvas, key.bounds);
                continue;
            }
            String label = key.value;
            if (key.type == ENTER) label = enterLabel;
            else if (key.type == SHIFT && locked) label = "⇪";
            else if (key.type == TEXT && shifted && mode == Mode.TEXT && !symbols)
                label = key.uppercase;
            else if (key.type == SPACE) label = "español";
            paint.setColor(FOREGROUND);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(key.type != TEXT || label.length() > 1);
            float textSize = Math.min(20 * density, key.bounds.height() * .46f);
            if (label.length() > 2) textSize = Math.min(14 * density, key.bounds.width() / (label.length() * .62f));
            paint.setTextSize(textSize);
            paint.getFontMetrics(fontMetrics);
            canvas.drawText(label, key.bounds.centerX(), key.bounds.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2, paint);
        }
        if (popup && alternatives != null) {
            for (int i = 0; i < alternatives.length; i++) {
                float left = popupLeft + i * popupCellWidth;
                paint.setColor(i == selectedAlternative ? ACTION : SPECIAL);
                canvas.drawRect(left, popupTop, left + popupCellWidth, popupTop + popupHeight, paint);
                paint.setColor(FOREGROUND);
                paint.setTextSize(Math.min(20 * density, popupHeight * .48f));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setFakeBoldText(false);
                paint.getFontMetrics(fontMetrics);
                canvas.drawText(alternatives[i], left + popupCellWidth / 2, popupTop + popupHeight / 2 - (fontMetrics.ascent + fontMetrics.descent) / 2, paint);
            }
        }
    }
    private void drawGlobe(Canvas canvas, RectF bounds) {
        float cx = bounds.centerX(), cy = bounds.centerY();
        float radius = Math.min(9 * density, Math.min(bounds.width(), bounds.height()) * .3f);
        paint.setColor(FOREGROUND);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f * density);
        canvas.drawCircle(cx, cy, radius, paint);
        canvas.drawOval(cx - radius * .45f, cy - radius, cx + radius * .45f, cy + radius, paint);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
        paint.setStyle(Paint.Style.FILL);
    }
    private static String alternativesFor(String value) {
        switch (value) {
            case "a": return "áàâäãå";
            case "e": return "éèêë";
            case "i": return "íìîï";
            case "o": return "óòôöõ";
            case "u": return "úüùû";
            case "n": return "ñ";
            case "?": return "¿";
            case "!": return "¡";
            case ".": return ".,?!¿¡";
            case ",": return ",;:¿¡";
            default: return null;
        }
    }
    private void showAlternatives(String options) {
        if (active == null) return;
        alternatives = new String[options.length()];
        for (int i = 0; i < options.length(); i++) alternatives[i] = shifted ? options.substring(i, i + 1).toUpperCase(Locale.ROOT) : options.substring(i, i + 1);
        popupCellWidth = Math.min(Math.max(active.bounds.width(), 40 * density), (float) getWidth() / alternatives.length);
        popupHeight = Math.min(52 * density, getHeight() / 4f);
        popupLeft = Math.max(0, Math.min(getWidth() - popupCellWidth * alternatives.length,
                active.bounds.centerX() - popupCellWidth * alternatives.length / 2));
        popupTop = Math.max(0, active.bounds.top - popupHeight);
        popup = true;
        selectedAlternative = 0;
        invalidate();
    }
    private Key keyAt(float x, float y) {
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            float pad = 4 * density;
            if (x >= key.bounds.left - pad && x <= key.bounds.right + pad
                    && y >= key.bounds.top - pad && y <= key.bounds.bottom + pad) return key;
        }
        return null;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelTouch();
                pointerId = event.getPointerId(0);
                downX = event.getX(); downY = event.getY();
                active = keyAt(downX, downY);
                if (active != null) handler.postDelayed(longPress, longPressDelay);
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN: {
                // Finish only ordinary text on rollover; special keys require a deliberate release.
                if (active != null && !moved && !popup
                        && (active.type == TEXT || active.type == SPACE)) dispatch(active);
                cancelTouch();
                int index = event.getActionIndex();
                pointerId = event.getPointerId(index);
                downX = event.getX(index); downY = event.getY(index);
                active = keyAt(downX, downY);
                if (active != null) handler.postDelayed(longPress, longPressDelay);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int index = event.findPointerIndex(pointerId);
                if (index < 0 || active == null) { cancelTouch(); return true; }
                float x = event.getX(index), y = event.getY(index);
                if (popup) {
                    // A stationary hold chooses the first, most common Spanish accent.
                    if (Math.abs(x - downX) > 8 * density) {
                        selectedAlternative = Math.max(0, Math.min(alternatives.length - 1,
                                (int) ((x - popupLeft) / popupCellWidth)));
                        invalidate();
                    }
                } else if (!active.bounds.contains(x, y)
                        && (Math.abs(x - downX) > 12 * density || Math.abs(y - downY) > 12 * density)) {
                    moved = true;
                    handler.removeCallbacks(longPress);
                    handler.removeCallbacks(repeatDelete);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == pointerId) {
                    if (active != null) {
                        if (popup && selectedAlternative >= 0) {
                            listener.onText(alternatives[selectedAlternative]);
                        } else if (!moved) dispatch(active);
                    }
                    cancelTouch();
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelTouch();
                return true;
            default: return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private void dispatch(Key key) {
        switch (key.type) {
            case SHIFT: listener.onShift(); break;
            case DELETE: if (!deleteRepeated) listener.onBackspace(); break;
            case ENTER: listener.onEnter(); break;
            case SWITCH: listener.onSwitchKeyboard(); break;
            case SYMBOLS: symbols = !symbols; moreSymbols = false; rebuildKeys(); invalidate(); break;
            case MORE_SYMBOLS: moreSymbols = !moreSymbols; rebuildKeys(); invalidate(); break;
            case SPACE: listener.onText(" "); break;
            default: listener.onText(shifted && mode == Mode.TEXT && !symbols
                    ? key.uppercase : key.value);
        }
    }
    private void cancelTouch() {
        handler.removeCallbacks(longPress);
        handler.removeCallbacks(repeatDelete);
        active = null;
        pointerId = MotionEvent.INVALID_POINTER_ID;
        popup = false;
        alternatives = null;
        selectedAlternative = -1;
        moved = false;
        deleteRepeated = false;
        invalidate();
    }
}
