package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.config.IiifProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.function.Function;

/**
 * Keeps user-supplied IIIF URLs from reaching the deployment's internal network (SSRF).
 *
 * <p>Every request target, including each redirect hop, must use HTTP(S) and resolve only to
 * public addresses unless {@code larex.iiif.allow-private-network-hosts} is enabled for
 * deployments whose IIIF servers live on an internal network.
 */
@Component
public class IiifRemoteUrlGuard {

    static final int MAX_REDIRECTS = 5;

    private final boolean allowPrivateNetworkHosts;

    @Autowired
    public IiifRemoteUrlGuard(IiifProperties properties) {
        this(properties.isAllowPrivateNetworkHosts());
    }

    public IiifRemoteUrlGuard(boolean allowPrivateNetworkHosts) {
        this.allowPrivateNetworkHosts = allowPrivateNetworkHosts;
    }

    /** HTTP clients used with this guard must not follow redirects themselves. */
    public static HttpClient.Builder clientBuilder() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER);
    }

    public void assertAllowed(URI uri) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only HTTP and HTTPS IIIF URLs are supported.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("IIIF URL must include a host name.");
        }
        if (allowPrivateNetworkHosts) {
            return;
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (isNonPublic(address)) {
                throw new IllegalArgumentException(
                        "IIIF URLs pointing to local or private network addresses are not allowed.");
            }
        }
    }

    /**
     * Sends a request and follows redirects manually so that every hop is checked.
     * Bodies of intermediate redirect responses are discarded.
     */
    public <T> HttpResponse<T> send(HttpClient client,
                                    URI uri,
                                    Function<URI, HttpRequest> requestFactory,
                                    HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            assertAllowed(current);
            HttpResponse<T> response = client.send(requestFactory.apply(current), bodyHandler);
            int status = response.statusCode();
            if (status < 300 || status >= 400 || status == 304) {
                return response;
            }
            String location = response.headers().firstValue("location").orElse(null);
            discard(response.body());
            if (location == null || location.isBlank()) {
                throw new IOException("IIIF server returned a redirect without a location (HTTP " + status + ").");
            }
            current = current.resolve(location.trim());
        }
        throw new IOException("IIIF server redirected too many times.");
    }

    static boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                    || (first == 100 && second >= 64 && second <= 127) // carrier-grade NAT
                    || (first == 192 && second == 0 && (bytes[2] & 0xff) == 0) // IETF protocol assignments
                    || (first == 198 && (second == 18 || second == 19)) // benchmarking
                    || first >= 240; // reserved and broadcast
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            if ((first & 0xfe) == 0xfc) { // unique local fc00::/7
                return true;
            }
            if (isIpv4Mapped(bytes)) {
                try {
                    byte[] v4 = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
                    return isNonPublic(InetAddress.getByAddress(v4));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
    }

    private static void discard(Object body) {
        if (body instanceof InputStream stream) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // The redirect body is irrelevant.
            }
        }
    }
}
