package tech.peeerly.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The whole app is the website in one WebView, starting on the same first page
 * as the site: the landing page, or the dashboard when logged in.
 */
public final class MainActivity extends ComponentActivity implements SiteWebViewClient.Host {

    private static final String TAG = "PeeerlyWeb";
    // Never hold the splash longer than this, even on a very slow network.
    private static final long SPLASH_LIMIT_MS = 4000;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final long startedAt = SystemClock.elapsedRealtime();

    private View root;
    private View skeleton;
    private View errorView;
    private WebView webView;

    private SiteChromeClient chrome;
    private Downloads downloads;
    private GoogleLogin googleLogin;

    private String currentUrl = "";
    private String failedUrl;
    private boolean keepSplash = true;
    private boolean firstPageShown;
    private Boolean darkPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splash.setKeepOnScreenCondition(() -> keepSplash);
        new Handler(Looper.getMainLooper()).postDelayed(() -> keepSplash = false, SPLASH_LIMIT_MS);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        skeleton = findViewById(R.id.skeleton);
        errorView = findViewById(R.id.error_view);
        webView = findViewById(R.id.web_view);

        downloads = new Downloads(this, io);
        googleLogin = new GoogleLogin(this, io, this::open);
        chrome = new SiteChromeClient(
                this, findViewById(R.id.fullscreen_holder), findViewById(R.id.progress));

        applyInsets();
        setUpWebView();
        setUpBack();
        findViewById(R.id.retry_button).setOnClickListener(v -> retry());
        findViewById(R.id.error_home_button).setOnClickListener(v -> open(startPath()));

        // Shown only if the page is still loading when the splash ends.
        skeleton.setVisibility(View.VISIBLE);
        webView.loadUrl(Routes.ORIGIN + startPath());
    }

    /**
     * The site's first page. When logged in, "/" only redirects to the dashboard,
     * so going there directly skips a whole server round trip.
     */
    private static String startPath() {
        String cookies = CookieManager.getInstance().getCookie(Routes.ORIGIN);
        boolean loggedIn = cookies != null && cookies.contains("better-auth.session_token=");
        return loggedIn ? Routes.DASHBOARD : Routes.START;
    }

    private void applyInsets() {
        View content = findViewById(R.id.content);
        View fullscreen = findViewById(R.id.fullscreen_holder);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            content.setPadding(
                    bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            fullscreen.setPadding(0, 0, 0, keyboard.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    // JavaScript is required by Next.js. Only our own site can load in this WebView.
    @SuppressLint("SetJavaScriptEnabled")
    private void setUpWebView() {
        boolean debug = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debug);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setBackgroundColor(getColor(R.color.canvas));
        webView.setWebViewClient(new SiteWebViewClient(this));
        webView.setWebChromeClient(chrome);
        webView.setDownloadListener((url, agent, disposition, mime, length) -> {
            pageShown();
            // blob: downloads are handled by PageBridge.
            if (url.startsWith("blob:")) return;
            if (Routes.isInternal(url)) downloads.fromUrl(url);
            else onExternalLink(url);
        });
        PageBridge.install(webView, downloads, this::applyPageTheme);
    }

    private void setUpBack() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                boolean error = errorView.getVisibility() == View.VISIBLE;
                if (Routes.isLivePlay(currentUrl) && !error) {
                    confirmLeavingQuiz();
                } else if (chrome.exitFullscreen()) {
                    return;
                } else if (!error && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    // Keep the page alive, so reopening the app is instant.
                    moveTaskToBack(true);
                }
            }
        });
    }

    private void confirmLeavingQuiz() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.leave_quiz_title)
                .setMessage(R.string.leave_quiz_message)
                .setNegativeButton(R.string.stay, null)
                .setPositiveButton(R.string.leave, (dialog, which) -> {
                    chrome.exitFullscreen();
                    if (webView.canGoBack()) webView.goBack();
                    else open(startPath());
                })
                .show();
    }

    private void open(String path) {
        errorView.setVisibility(View.GONE);
        skeleton.setVisibility(View.VISIBLE);
        failedUrl = null;
        webView.loadUrl(Routes.ORIGIN + path);
        webView.requestFocus();
    }

    private void retry() {
        String url = failedUrl != null ? failedUrl : Routes.ORIGIN + startPath();
        failedUrl = null;
        errorView.setVisibility(View.GONE);
        skeleton.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
    }

    private void pageShown() {
        keepSplash = false;
        skeleton.setVisibility(View.GONE);
        if (!firstPageShown) {
            firstPageShown = true;
            Log.i(TAG, "First page visible after " + (SystemClock.elapsedRealtime() - startedAt) + " ms");
        }
    }

    /** Matches the status bar and background to the site's light or dark mode. */
    private void applyPageTheme(boolean dark) {
        if (darkPage != null && darkPage == dark) return;
        darkPage = dark;
        int color = getColor(dark ? R.color.page_dark : R.color.page_light);
        root.setBackgroundColor(color);
        webView.setBackgroundColor(color);
        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(!dark);
        bars.setAppearanceLightNavigationBars(!dark);
    }

    private void updateKeepScreenOn() {
        if (Routes.keepsScreenOn(currentUrl)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onPageStart(String url) {
        failedUrl = null;
        errorView.setVisibility(View.GONE);
    }

    @Override
    public void onPageVisible() {
        if (failedUrl == null) pageShown();
    }

    @Override
    public void onPageDone() {
        if (failedUrl == null) pageShown();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onPageError(String url) {
        failedUrl = url;
        keepSplash = false;
        skeleton.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onAddressChanged(String url) {
        currentUrl = url;
        updateKeepScreenOn();
    }

    @Override
    public void onGoogleLogin() {
        pageShown();
        String callback = Routes.isInternal(currentUrl)
                ? Uri.parse(currentUrl).getQueryParameter("callbackUrl")
                : null;
        googleLogin.start(Routes.afterLogin(callback));
    }

    @Override
    public void onDocument(String url) {
        pageShown();
        downloads.fromUrl(url);
    }

    @Override
    public void onExternalLink(String url) {
        pageShown();
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.matches("(?i)https?|mailto|tel")) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onWebViewCrashed() {
        recreate();
    }

    @Override
    protected void onStop() {
        CookieManager.getInstance().flush();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        ((ViewGroup) webView.getParent()).removeView(webView);
        webView.destroy();
        super.onDestroy();
    }
}
