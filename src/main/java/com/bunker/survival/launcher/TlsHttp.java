package com.bunker.survival.launcher;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Accepts the site's self-signed HTTPS certificate. */
public final class TlsHttp {
    private TlsHttp() {
    }

    public static HttpClient.Builder clientBuilder() {
        try {
            TrustManager[] trust = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trust, new SecureRandom());
            SSLParameters params = new SSLParameters();
            params.setEndpointIdentificationAlgorithm("");
            return HttpClient.newBuilder().sslContext(ctx).sslParameters(params);
        } catch (Exception e) {
            return HttpClient.newBuilder();
        }
    }
}
