package pe.gustavo.tecladolibre;

import android.test.InstrumentationTestCase;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

/** Exercises service callbacks against Android's real editable input connection. */
public class ServiceTest extends InstrumentationTestCase {
    private static final class TestService extends LibreInputMethodService {
        InputConnection connection;
        @Override public InputConnection getCurrentInputConnection() { return connection; }
    }

    public void testSelectionReplacementAndUnicodeBackspace() throws Throwable {
        runTestOnUiThread(() -> {
            EditText edit = new EditText(getInstrumentation().getTargetContext());
            edit.setText("ab\uD83D\uDE00");
            TestService service = new TestService();
            service.connection = edit.onCreateInputConnection(new EditorInfo());
            EditorInfo info = new EditorInfo();
            info.inputType = InputType.TYPE_CLASS_TEXT;
            service.onStartInput(info, false);
            edit.setSelection(1, 2);
            service.onText("x");
            assertEquals("ax\uD83D\uDE00", edit.getText().toString());
            edit.setSelection(edit.length());
            service.onBackspace();
            assertEquals("ax", edit.getText().toString());
        });
    }

    public void testCustomActionAndRawKeys() throws Throwable {
        runTestOnUiThread(() -> {
            EditText edit = new EditText(getInstrumentation().getTargetContext());
            final int[] action = {-1};
            final int[] keys = {0};
            TestService service = new TestService();
            service.connection = new InputConnectionWrapper(edit.onCreateInputConnection(new EditorInfo()), false) {
                @Override public boolean performEditorAction(int id) { action[0] = id; return true; }
                @Override public boolean sendKeyEvent(KeyEvent event) { keys[0]++; return true; }
            };
            EditorInfo info = new EditorInfo();
            info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            info.actionLabel = "Custom";
            info.actionId = 42;
            service.onStartInput(info, false);
            service.onEnter();
            assertEquals(42, action[0]);
            info = new EditorInfo();
            info.inputType = InputType.TYPE_NULL;
            service.onStartInput(info, false);
            service.onBackspace();
            service.onEnter();
            assertEquals(4, keys[0]);
        });
    }
}
