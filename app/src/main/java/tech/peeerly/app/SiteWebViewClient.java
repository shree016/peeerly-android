package tech.peeerly.app;

import android.graphics.Bitmap;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Follows what the WebView is loading and decides which links stay inside it. */
final class SiteWebViewClient extends WebViewClient {

    interface Host {
        void onPageStart(String url);

        void onPageVisible();

        void onPageDone();

        void onPageError(String url);

        void onAddressChanged(String url);

        void onGoogleLogin();

        void onDocument(String url);

        void onExternalLink(String url);

        void onWebViewCrashed();
    }

    private final Host host;

    SiteWebViewClient(Host host) {
        this.host = host;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (!request.isForMainFrame()) return false;
        String url = request.getUrl().toString();

        if (Routes.isGoogleLogin(url)) {
            host.onGoogleLogin();
            return true;
        }
        if (Routes.isDocument(url)) {
            host.onDocument(url);
            return true;
        }
        if (Routes.isInternal(url)) return false;

        host.onExternalLink(url);
        return true;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        host.onPageStart(url);
    }

    @Override
    public void onPageCommitVisible(WebView view, String url) {
        host.onPageVisible();
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        host.onPageDone();
    }

    // Also fires for Next.js client-side navigation, which loads no new page.
    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        host.onAddressChanged(url);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (request.isForMainFrame()) host.onPageError(request.getUrl().toString());
    }

    @Override
    public void onReceivedHttpError(
            WebView view, WebResourceRequest request, WebResourceResponse response) {
        if (request.isForMainFrame() && response.getStatusCode() >= 500) {
            host.onPageError(request.getUrl().toString());
        }
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        host.onWebViewCrashed();
        return true;
    }
}
