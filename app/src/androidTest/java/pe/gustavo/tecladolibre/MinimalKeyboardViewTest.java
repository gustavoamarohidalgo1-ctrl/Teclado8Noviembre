package pe.gustavo.tecladolibre;

import android.graphics.RectF;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.lang.reflect.Field;
import java.util.ArrayList;

/** Exercises the real view and main-looper touch timers without external dependencies. */
public class MinimalKeyboardViewTest extends InstrumentationTestCase {
    private MinimalKeyboardView view;
    private final StringBuilder committed = new StringBuilder();
    private int deletes;
    private long downTime;

    @Override protected void setUp() throws Exception {
        super.setUp();
        getInstrumentation().runOnMainSync(() -> {
            view = new MinimalKeyboardView(getInstrumentation().getTargetContext(), new MinimalKeyboardView.Listener() {
                public void onText(String text) { committed.append(text); }
                public void onBackspace() { deletes++; }
                public void onEnter() { }
                public void onShift() { }
                public void onSwitchKeyboard() { }
            });
            view.setPadding(20, 10, 30, 80);
            view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, 1080, 600);
        });
    }
    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> view.release());
        super.tearDown();
    }
    private RectF bounds(String label) throws Exception {
        Field keysField = MinimalKeyboardView.class.getDeclaredField("keys");
        keysField.setAccessible(true);
        for (Object key : (ArrayList<?>) keysField.get(view)) {
            Field value = key.getClass().getDeclaredField("value"); value.setAccessible(true);
            if (label.equals(value.get(key))) {
                Field rect = key.getClass().getDeclaredField("bounds"); rect.setAccessible(true);
                return new RectF((RectF) rect.get(key));
            }
        }
        throw new AssertionError("Missing key " + label);
    }
    private void event(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent e = MotionEvent.obtain(downTime, now, action, x, y, 0);
        getInstrumentation().runOnMainSync(() -> view.onTouchEvent(e));
        e.recycle();
    }
    private void tap(String label) throws Exception {
        RectF r = bounds(label);
        event(MotionEvent.ACTION_DOWN, r.centerX(), r.centerY());
        event(MotionEvent.ACTION_UP, r.centerX(), r.centerY());
    }
    public void testSymbolsAndDateTimeAreReachable() throws Exception {
        tap("?123");
        for (String s : new String[]{"1", "@", "#", "$", "%", "&", "-", "+", "(", ")", "/", "=", "*", "\"", "'", ":", ";", "!", "?"}) tap(s);
        tap("#+=");
        for (String s : new String[]{"[", "]", "{", "}", "<", ">", "\\", "|", "^", "~", "_", "`", "€", "£"}) tap(s);
        assertEquals("1@#$%&-+()/=*\"':;!?[]{}<>\\|^~_`€£", committed.toString());
        getInstrumentation().runOnMainSync(() -> view.setMode(MinimalKeyboardView.Mode.DATETIME));
        for (String s : new String[]{"1", "/", ":", "-", ".", " "}) tap(s);
        assertTrue(committed.toString().endsWith("1/:-. "));
    }
    public void testStationaryAccentAndCancel() throws Exception {
        RectF a = bounds("a");
        event(MotionEvent.ACTION_DOWN, a.centerX(), a.centerY());
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 100);
        event(MotionEvent.ACTION_UP, a.centerX(), a.centerY());
        assertEquals("á", committed.toString());
        event(MotionEvent.ACTION_DOWN, a.centerX(), a.centerY());
        event(MotionEvent.ACTION_CANCEL, a.centerX(), a.centerY());
        assertEquals("á", committed.toString());
    }
    public void testDeleteRepeatDoesNotDeleteAgainOnRelease() throws Exception {
        RectF r = bounds("⌫");
        event(MotionEvent.ACTION_DOWN, r.centerX(), r.centerY());
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 170);
        getInstrumentation().waitForIdleSync();
        int beforeRelease = deletes;
        assertTrue(beforeRelease >= 1);
        event(MotionEvent.ACTION_UP, r.centerX(), r.centerY());
        assertEquals(beforeRelease, deletes);
    }
    public void testPaddingDoesNotContainKeys() throws Exception {
        RectF q = bounds("q"), space = bounds(" ");
        assertTrue(q.left >= 20 && q.top >= 10);
        assertTrue(space.bottom <= 520);
        event(MotionEvent.ACTION_DOWN, 500, 570);
        event(MotionEvent.ACTION_UP, 500, 570);
        assertEquals("", committed.toString());
    }
    public void testTwoThumbRolloverCommitsOnce() throws Exception {
        RectF a = bounds("a"), b = bounds("b");
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties p0 = new MotionEvent.PointerProperties(); p0.id = 0; p0.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerProperties p1 = new MotionEvent.PointerProperties(); p1.id = 1; p1.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords c0 = new MotionEvent.PointerCoords(); c0.x = a.centerX(); c0.y = a.centerY(); c0.pressure = 1; c0.size = 1;
        MotionEvent.PointerCoords c1 = new MotionEvent.PointerCoords(); c1.x = b.centerX(); c1.y = b.centerY(); c1.pressure = 1; c1.size = 1;
        event(MotionEvent.ACTION_DOWN, c0.x, c0.y);
        sendMulti(now, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), p0, p1, c0, c1);
        sendMulti(now, MotionEvent.ACTION_POINTER_UP, p0, p1, c0, c1);
        MotionEvent up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 1,
                new MotionEvent.PointerProperties[]{p1}, new MotionEvent.PointerCoords[]{c1},
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        getInstrumentation().runOnMainSync(() -> view.onTouchEvent(up));
        up.recycle();
        assertEquals("ab", committed.toString());
    }
    private void sendMulti(long now, int action, MotionEvent.PointerProperties p0, MotionEvent.PointerProperties p1,
                           MotionEvent.PointerCoords c0, MotionEvent.PointerCoords c1) {
        MotionEvent e = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, 2,
                new MotionEvent.PointerProperties[]{p0, p1}, new MotionEvent.PointerCoords[]{c0, c1},
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        getInstrumentation().runOnMainSync(() -> view.onTouchEvent(e));
        e.recycle();
    }
}
