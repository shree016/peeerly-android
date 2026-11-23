package tech.peeerly.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** The browser features a plain WebView lacks: fullscreen, file picker, progress. */
final class SiteChromeClient extends WebChromeClient {

    private static final String TAG = "PeeerlyWeb";

    private final ComponentActivity activity;
    private final FrameLayout fullscreenHolder;
    private final ProgressBar progress;
    private final ActivityResultLauncher<Intent> picker;

    private ValueCallback<Uri[]> pendingFiles;
    private CustomViewCallback fullscreenCallback;
    // An OnBackInvokedCallback; typed as Object so Android 10-12 never loads that class.
    private Object backAboveWebView;

    SiteChromeClient(ComponentActivity activity, FrameLayout fullscreenHolder, ProgressBar progress) {
        this.activity = activity;
        this.fullscreenHolder = fullscreenHolder;
        this.progress = progress;
        this.picker = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> deliverFiles(pickedFiles(result.getResultCode(), result.getData())));
    }

    /**
     * WebView's parseResult reads only getData(), but a picker opened for several
     * files returns them in ClipData, which left fields such as the logo empty.
     */
    static Uri[] pickedFiles(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return null;
        ClipData clip = data.getClipData();
        if (clip != null && clip.getItemCount() > 0) {
            Uri[] uris = new Uri[clip.getItemCount()];
            for (int i = 0; i < uris.length; i++) uris[i] = clip.getItemAt(i).getUri();
            return uris;
        }
        return data.getData() == null ? null : new Uri[] {data.getData()};
    }

    @Override
    public void onProgressChanged(WebView view, int percent) {
        progress.setProgress(percent);
        progress.setVisibility(percent < 100 ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public boolean onShowFileChooser(
            WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        deliverFiles(null);
        pendingFiles = callback;

        // Android reports CSV and JSON under many MIME types, so only narrow the
        // picker for image fields. The website validates every file anyway.
        boolean imagesOnly = params.getAcceptTypes().length > 0;
        for (String accepted : params.getAcceptTypes()) {
            for (String type : accepted.split(",")) {
                String t = type.trim();
                if (!t.startsWith("image/") && !t.matches("(?i)\\.(png|jpe?g|webp|gif|svg)")) {
                    imagesOnly = false;
                }
            }
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(imagesOnly ? "image/*" : "*/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                        params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
        try {
            picker.launch(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No file picker", e);
            deliverFiles(null);
            Toast.makeText(activity, R.string.no_file_picker, Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private void deliverFiles(Uri[] files) {
        if (pendingFiles == null) return;
        pendingFiles.onReceiveValue(files);
        pendingFiles = null;
    }

    // The WebView underneath must stay VISIBLE. Hiding it makes the page report
    // "hidden", which the quiz counts as switching tabs and can remove the student.
    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (fullscreenCallback != null) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreenCallback = callback;
        FullscreenFrame frame = new FullscreenFrame(activity);
        frame.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenHolder.addView(frame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenHolder.setVisibility(View.VISIBLE);
        view.requestFocus();

        // WebView registers its own Back handler for fullscreen, which would leave
        // fullscreen silently. Ours goes above it so a live quiz can ask first.
        if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedCallback back =
                    () -> activity.getOnBackPressedDispatcher().onBackPressed();
            backAboveWebView = back;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY, back);
        }

        WindowInsetsControllerCompat bars = bars();
        bars.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        bars.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onHideCustomView() {
        exitFullscreen();
    }

    boolean exitFullscreen() {
        if (fullscreenCallback == null) return false;
        CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;
        if (Build.VERSION.SDK_INT >= 33 && backAboveWebView != null) {
            activity.getOnBackInvokedDispatcher()
                    .unregisterOnBackInvokedCallback((OnBackInvokedCallback) backAboveWebView);
            backAboveWebView = null;
        }
        // Detach WebView's view from our frame, or the next fullscreen can't re-attach it.
        for (int i = 0; i < fullscreenHolder.getChildCount(); i++) {
            ((ViewGroup) fullscreenHolder.getChildAt(i)).removeAllViews();
        }
        fullscreenHolder.removeAllViews();
        fullscreenHolder.setVisibility(View.GONE);
        bars().show(WindowInsetsCompat.Type.systemBars());
        callback.onCustomViewHidden();
        return true;
    }

    private WindowInsetsControllerCompat bars() {
        return WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
    }
}
