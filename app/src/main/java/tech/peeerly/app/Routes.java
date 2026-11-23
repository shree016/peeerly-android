package tech.peeerly.app;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Every decision about which address opens where. Plain Java, so it can be unit tested. */
final class Routes {

    /** The bare domain redirects to www, so we go straight there and save a round trip. */
    static final String ORIGIN = "https://www.peeerly.tech";

    /** The site sends this to the dashboard when logged in, like in a browser. */
    static final String START = "/";
    static final String DASHBOARD = "/dashboard";

    private static final String DOCUMENTS = "/api/experiences/v1/documents";
    private static final Pattern LIVE_PLAY = Pattern.compile("^/quiz/play/[^/]+/?$");
    private static final Pattern PRESENTER = Pattern.compile("^/quiz/sessions/[^/]+/present.*");
    private static final Pattern POLL_WALL = Pattern.compile("^/polls/[^/]+/wall.*");

    private Routes() {}

    private static URI parse(String url) {
        if (url == null) return null;
        // The query may hold characters java.net.URI rejects, and we never need it.
        String base = url.split("[?#]", 2)[0];
        try {
            return new URI(base);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** True only for our own HTTPS site. Only these addresses may load inside the WebView. */
    static boolean isInternal(String url) {
        URI uri = parse(url);
        if (uri == null || uri.getHost() == null || uri.getRawUserInfo() != null) return false;
        if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
        if (uri.getPort() != -1 && uri.getPort() != 443) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("www.peeerly.tech") || host.equals("peeerly.tech");
    }

    static String path(String url) {
        if (!isInternal(url)) return "";
        String path = parse(url).getPath();
        return path.isEmpty() ? "/" : path;
    }

    static boolean isGoogleLogin(String url) {
        URI uri = parse(url);
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && "accounts.google.com".equalsIgnoreCase(uri.getHost());
    }

    /** PDFs from this route are sent inline, and WebView cannot display PDFs. */
    static boolean isDocument(String url) {
        return path(url).equals(DOCUMENTS);
    }

    /** A quiz being played (not its report), where leaving needs a confirmation. */
    static boolean isLivePlay(String url) {
        return LIVE_PLAY.matcher(path(url)).matches();
    }

    static boolean keepsScreenOn(String url) {
        String path = path(url);
        return isLivePlay(url)
                || PRESENTER.matcher(path).matches()
                || POLL_WALL.matcher(path).matches();
    }

    /** Where to go after login. Never lets a link send the user to another site. */
    static String afterLogin(String callback) {
        if (callback == null || !callback.startsWith("/") || callback.startsWith("//")
                || callback.contains("\\") || !isInternal(ORIGIN + callback)) {
            return DASHBOARD;
        }
        return callback;
    }
}
