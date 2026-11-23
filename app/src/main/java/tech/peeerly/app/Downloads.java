package tech.peeerly.app;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Saves the site's PDF, CSV and document downloads into the phone's Downloads folder. */
final class Downloads {

    private static final String TAG = "PeeerlyDownloads";
    private static final int MAX_BYTES = 25 * 1024 * 1024;
    private static final Pattern FILE_NAME = Pattern.compile("filename=\"([^\"]+)\"");

    // Only document types. An app or script can never be saved through this path.
    private static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf",
            "csv", "text/csv",
            "json", "application/json",
            "txt", "text/plain",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "png", "image/png",
            "jpg", "image/jpeg",
            "webp", "image/webp");

    private final ComponentActivity activity;
    private final ExecutorService io;

    Downloads(ComponentActivity activity, ExecutorService io) {
        this.activity = activity;
        this.io = io;
    }

    void fromUrl(String url) {
        if (!Routes.isInternal(url)) return;
        toast(R.string.downloading);
        String cookies = CookieManager.getInstance().getCookie(url);
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                // Never follow a redirect: it could carry the login cookie to another site.
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(30_000);
                if (cookies != null) connection.setRequestProperty("Cookie", cookies);
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + connection.getResponseCode());
                }

                String disposition = connection.getHeaderField("Content-Disposition");
                String mime = connection.getContentType();
                if (mime != null) mime = mime.split(";")[0].trim();

                String name = null;
                if (disposition != null) {
                    Matcher matcher = FILE_NAME.matcher(disposition);
                    if (matcher.find()) name = matcher.group(1);
                }
                if (name == null) name = URLUtil.guessFileName(url, disposition, mime);

                try (InputStream in = connection.getInputStream()) {
                    save(name, mime, readLimited(in));
                }
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Download failed", e);
                toast(R.string.download_failed);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    void fromBase64(String name, String mime, String data) {
        if (data.length() > MAX_BYTES / 3 * 4) {
            toast(R.string.download_failed);
            return;
        }
        io.execute(() -> {
            try {
                save(name, mime, Base64.decode(data, Base64.DEFAULT));
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Saving the file failed", e);
                toast(R.string.download_failed);
            }
        });
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > MAX_BYTES) throw new IOException("File is too large");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private void save(String rawName, String reportedMime, byte[] bytes) throws IOException {
        String name = rawName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        String extension = name.contains(".")
                ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";

        if (!TYPES.containsKey(extension)) {
            extension = "";
            for (Map.Entry<String, String> type : TYPES.entrySet()) {
                if (type.getValue().equals(reportedMime)) extension = type.getKey();
            }
            if (extension.isEmpty()) throw new IOException("File type is not allowed");
            name = name + "." + extension;
        }
        String mime = TYPES.get(extension);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        ContentResolver resolver = activity.getContentResolver();
        Uri saved = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (saved == null) throw new IOException("Could not create the file");

        try (OutputStream out = resolver.openOutputStream(saved)) {
            if (out == null) throw new IOException("Could not write the file");
            out.write(bytes);
        } catch (IOException e) {
            resolver.delete(saved, null, null);
            throw e;
        }

        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(saved, values, null, null);

        toast(R.string.download_saved);
        activity.runOnUiThread(() -> open(saved, mime));
    }

    private void open(Uri file, String mime) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(file, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (ActivityNotFoundException e) {
            toast(R.string.no_viewer);
        }
    }

    private void toast(int message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }
}
