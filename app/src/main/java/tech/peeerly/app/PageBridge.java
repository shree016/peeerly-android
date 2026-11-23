package tech.peeerly.app;

import android.annotation.SuppressLint;
import android.util.Log;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;
import java.util.function.Consumer;

/**
 * The only channel between the website and Java. It is limited to our origin
 * and understands two messages: "the page's theme changed" and "save this file".
 */
final class PageBridge {

    private static final String TAG = "PeeerlyBridge";
    private static final String NAME = "PeeerlyApp";

    // The site revokes blob: URLs straight after clicking them, so keep the blob
    // ourselves. The theme comes from the "dark" class that next-themes sets.
    private static final String SCRIPT = """
            (() => {
              if (window.top !== window) return;
              const send = (message) => window.PeeerlyApp.postMessage(JSON.stringify(message));

              const html = document.documentElement;
              let lastDark = null;
              const sendTheme = () => {
                const dark = html.classList.contains('dark');
                if (dark === lastDark) return;
                lastDark = dark;
                send({ type: 'theme', dark });
              };
              new MutationObserver(sendTheme).observe(html, { attributes: true, attributeFilter: ['class'] });
              document.addEventListener('DOMContentLoaded', sendTheme);
              sendTheme();

              const blobs = new Map();
              const createUrl = URL.createObjectURL;
              URL.createObjectURL = function (object) {
                const url = createUrl.apply(this, arguments);
                if (object instanceof Blob) {
                  blobs.set(url, object);
                  setTimeout(() => blobs.delete(url), 60000);
                }
                return url;
              };
              const click = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function () {
                const blob = blobs.get(this.href);
                if (!blob || !this.hasAttribute('download')) return click.apply(this, arguments);
                const name = this.download || 'download';
                const reader = new FileReader();
                reader.onload = () => send({
                  type: 'download',
                  name,
                  mime: blob.type.split(';')[0],
                  data: String(reader.result).split(',')[1],
                });
                reader.readAsDataURL(blob);
              };
            })();
            """;

    private PageBridge() {}

    // The listener is restricted to our HTTPS origin and checks isMainFrame.
    @SuppressLint("RequiresFeature")
    static void install(WebView webView, Downloads downloads, Consumer<Boolean> onTheme) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.w(TAG, "WebView is too old for blob downloads and theme sync");
            return;
        }

        Set<String> origins = Set.of(Routes.ORIGIN);
        WebViewCompat.addWebMessageListener(webView, NAME, origins,
                (view, message, sourceOrigin, isMainFrame, reply) -> {
                    if (!isMainFrame || message.getData() == null) return;
                    try {
                        JSONObject json = new JSONObject(message.getData());
                        String type = json.optString("type");
                        if (type.equals("theme")) {
                            onTheme.accept(json.optBoolean("dark"));
                        } else if (type.equals("download")) {
                            downloads.fromBase64(
                                    json.getString("name"),
                                    json.optString("mime"),
                                    json.getString("data"));
                        }
                    } catch (JSONException e) {
                        Log.w(TAG, "Ignored a malformed message", e);
                    }
                });
        WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, origins);
    }
}
