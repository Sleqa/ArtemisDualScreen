package com.limelight.dualscreen;

import android.os.Handler;
import android.os.Looper;

import com.limelight.LimeLog;

import java.io.IOException;
import java.net.Proxy;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Polls Vibepollo's host statistics API ({@code GET /api/host/stats} on the web UI port, which is
 * the host's base HTTP port + 1) so the second-screen panel can show the host PC's CPU/GPU/RAM
 * load next to the client-side stream stats.
 *
 * <p>The web UI is served with the same certificate as the streaming server, so the paired
 * server certificate is used to pin the connection. The API requires credentials: either the
 * Vibepollo web UI username/password (HTTP Basic) or a scoped API token (Bearer), both entered
 * in the app's settings.</p>
 */
public class HostStatsPoller {

    public interface Listener {
        /** New snapshot from the host. */
        void onHostStats(HostStats stats);

        /**
         * The host stats could not be read.
         *
         * @param reason short user-facing reason, or null while the failure is transient
         *               (a dropped request that will simply be retried)
         */
        void onHostStatsUnavailable(String reason);
    }

    private static final long POLL_INTERVAL_MS = 2000;
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 4000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String url;
    private final String authHeader;
    private final OkHttpClient httpClient;
    private final Listener listener;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> pollTask;
    private volatile boolean stopped = false;

    /**
     * @param host       host address the stream is connected to
     * @param webPort    Vibepollo web UI HTTPS port
     * @param serverCert paired server certificate used to pin the connection, may be null
     * @param username   web UI username (ignored when a token is supplied)
     * @param password   web UI password (ignored when a token is supplied)
     * @param apiToken   scoped API token, takes precedence over username/password
     */
    public HostStatsPoller(String host, int webPort, X509Certificate serverCert,
                           String username, String password, String apiToken, Listener listener) {
        this.listener = listener;
        this.url = "https://" + formatHostForUrl(host) + ":" + webPort + "/api/host/stats";
        if (apiToken != null && !apiToken.isEmpty()) {
            this.authHeader = "Bearer " + apiToken;
        } else if (username != null && !username.isEmpty()) {
            this.authHeader = Credentials.basic(username, password != null ? password : "");
        } else {
            this.authHeader = null;
        }
        this.httpClient = buildHttpClient(serverCert);
    }

    public static boolean hasCredentials(String username, String apiToken) {
        return (apiToken != null && !apiToken.isEmpty()) || (username != null && !username.isEmpty());
    }

    public void start() {
        if (executor != null) {
            return;
        }
        if (authHeader == null) {
            notifyUnavailable("no credentials");
            return;
        }
        stopped = false;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "HostStatsPoller");
            thread.setDaemon(true);
            return thread;
        });
        pollTask = executor.scheduleWithFixedDelay(this::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        stopped = true;
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void poll() {
        if (stopped) {
            return;
        }
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                notifyUnavailable("host rejected the credentials");
                // Retrying with credentials the host has already refused only spams its log.
                stop();
                return;
            }
            if (response.code() == 404) {
                notifyUnavailable("host has no stats API");
                stop();
                return;
            }
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                notifyUnavailable(null);
                return;
            }
            HostStats stats = HostStats.fromJson(body.string());
            if (!stopped) {
                mainHandler.post(() -> listener.onHostStats(stats));
            }
        } catch (IOException e) {
            notifyUnavailable(null);
        } catch (Exception e) {
            LimeLog.warning("Host stats poll failed: " + e.getMessage());
            notifyUnavailable(null);
        }
    }

    private void notifyUnavailable(String reason) {
        if (!stopped || reason != null) {
            mainHandler.post(() -> listener.onHostStatsUnavailable(reason));
        }
    }

    private static String formatHostForUrl(String host) {
        // Bare IPv6 literals need bracketing before they can go into a URL
        if (host != null && host.contains(":") && !host.startsWith("[")) {
            return "[" + host + "]";
        }
        return host;
    }

    private static OkHttpClient buildHttpClient(final X509Certificate serverCert) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .proxy(Proxy.NO_PROXY);

        try {
            final X509TrustManager defaultTrustManager = getDefaultTrustManager();
            X509TrustManager pinningTrustManager = new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    throw new IllegalStateException("Should never be called");
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    try {
                        // Hosts fronted by a real CA-issued certificate validate normally
                        defaultTrustManager.checkServerTrusted(certs, authType);
                    } catch (CertificateException e) {
                        // Otherwise accept only the self-signed certificate we paired with
                        if (certs.length == 1 && serverCert != null) {
                            if (!certs[0].equals(serverCert)) {
                                throw new CertificateException("Certificate mismatch");
                            }
                        } else {
                            throw e;
                        }
                    }
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {pinningTrustManager}, new SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory(), pinningTrustManager);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    try {
                        java.security.cert.Certificate[] certificates = session.getPeerCertificates();
                        if (certificates.length == 1 && certificates[0].equals(serverCert)) {
                            // The pinned certificate carries no hostname for us to match against
                            return true;
                        }
                    } catch (SSLPeerUnverifiedException e) {
                        return false;
                    }
                    return HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session);
                }
            });
        } catch (Exception e) {
            LimeLog.warning("Unable to pin host stats connection: " + e.getMessage());
        }

        return builder.build();
    }

    private static X509TrustManager getDefaultTrustManager() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new IllegalStateException("No X509 trust manager found");
    }
}
