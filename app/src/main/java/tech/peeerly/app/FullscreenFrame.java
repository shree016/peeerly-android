package tech.peeerly.app;

import android.annotation.SuppressLint;
import android.view.KeyEvent;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;

/**
 * WebView's fullscreen view swallows the Back button and silently leaves
 * fullscreen. Catch Back first, so a live quiz can ask before anything happens.
 */
@SuppressLint("ViewConstructor") // only created from code, never from XML
final class FullscreenFrame extends FrameLayout {

    private final ComponentActivity activity;

    FullscreenFrame(ComponentActivity activity) {
        super(activity);
        this.activity = activity;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            activity.getOnBackPressedDispatcher().onBackPressed();
        }
        return true;
    }
}
