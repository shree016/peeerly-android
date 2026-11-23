package tech.peeerly.app;

import android.os.CancellationSignal;
import android.util.Log;
import android.webkit.CookieManager;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Google login without a WebView: Android's account picker gives an ID token,
 * the website's existing Better Auth endpoint checks it and returns the normal
 * session cookie, which we hand to the WebView.
 */
final class GoogleLogin {

    private static final String TAG = "PeeerlyLogin";

    private final ComponentActivity activity;
    private final ExecutorService io;
    private final Consumer<String> onSuccess;
    private boolean busy;

    GoogleLogin(ComponentActivity activity, ExecutorService io, Consumer<String> onSuccess) {
        this.activity = activity;
        this.io = io;
        this.onSuccess = onSuccess;
    }

    void start(String nextPath) {
        if (busy) return;
        busy = true;

        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(
                activity.getString(R.string.google_web_client_id)).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        CredentialManager.create(activity).getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                        .equals(credential.getType())) {
                            String token = GoogleIdTokenCredential
                                    .createFrom(credential.getData())
                                    .getIdToken();
                            io.execute(() -> exchange(token, nextPath));
                        } else {
                            fail(R.string.google_failed);
                        }
                    }

                    @Override
                    public void onError(GetCredentialException error) {
                        // A missing Android client / wrong SHA-1 is also reported here.
                        Log.w(TAG, "Google sign-in: " + error.getClass().getSimpleName() + " " + error.getMessage());
                        if (error instanceof NoCredentialException) {
                            fail(R.string.google_no_account);
                        } else if (error instanceof GetCredentialCancellationException) {
                            fail(R.string.google_cancelled);
                        } else {
                            fail(R.string.google_failed);
                        }
                    }
                });
    }

    private void exchange(String token, String nextPath) {
        HttpURLConnection connection = null;
        try {
            byte[] body = new JSONObject()
                    .put("provider", "google")
                    .put("idToken", new JSONObject().put("token", token))
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);

            connection = (HttpURLConnection)
                    new URL(Routes.ORIGIN + "/api/auth/sign-in/social").openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Website refused the Google login: HTTP " + status);
                // 400: not a college email. 401: an unlinked password account has this email.
                fail(status >= 400 && status < 500 ? R.string.google_refused : R.string.google_failed);
                return;
            }

            List<String> cookies = new ArrayList<>();
            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
                if ("set-cookie".equalsIgnoreCase(header.getKey())) cookies.addAll(header.getValue());
            }
            if (cookies.isEmpty()) {
                fail(R.string.google_failed);
                return;
            }

            activity.runOnUiThread(() -> {
                CookieManager manager = CookieManager.getInstance();
                for (int i = 0; i < cookies.size(); i++) {
                    boolean last = i == cookies.size() - 1;
                    manager.setCookie(Routes.ORIGIN, cookies.get(i), saved -> {
                        if (!last) return;
                        manager.flush();
                        busy = false;
                        if (!activity.isDestroyed()) onSuccess.accept(nextPath);
                    });
                }
            });
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Google login request failed", e);
            fail(R.string.google_failed);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void fail(int message) {
        activity.runOnUiThread(() -> {
            busy = false;
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        });
    }
}
