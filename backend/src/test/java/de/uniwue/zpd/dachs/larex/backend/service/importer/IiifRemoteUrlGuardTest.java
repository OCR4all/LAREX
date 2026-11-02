package de.uniwue.zpd.dachs.larex.backend.service.importer;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IiifRemoteUrlGuardTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void rejectsInternalAddresses() {
        IiifRemoteUrlGuard guard = new IiifRemoteUrlGuard(false);

        for (String url : new String[]{
                "http://127.0.0.1/manifest",
                "http://localhost:8080/manifest",
                "http://10.1.2.3/manifest",
                "http://172.20.0.5/manifest",
                "http://192.168.1.1/manifest",
                "http://169.254.169.254/latest/meta-data",
                "http://100.64.0.1/manifest",
                "http://[::1]/manifest",
                "http://[fd00::1]/manifest",
                "http://[::ffff:127.0.0.1]/manifest",
                "http://0.0.0.0/manifest"
        }) {
            assertThatThrownBy(() -> guard.assertAllowed(URI.create(url)))
                    .as(url)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsNonHttpSchemes() {
        IiifRemoteUrlGuard guard = new IiifRemoteUrlGuard(true);

        assertThatThrownBy(() -> guard.assertAllowed(URI.create("file:///etc/passwd")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void treatsPublicAddressesAsPublic() throws Exception {
        assertThat(IiifRemoteUrlGuard.isNonPublic(InetAddress.getByName("93.184.216.34"))).isFalse();
        assertThat(IiifRemoteUrlGuard.isNonPublic(InetAddress.getByName("2606:2800:220:1::1"))).isFalse();
    }

    @Test
    void checksEveryRedirectHop() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://169.254.169.254/latest/meta-data");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        URI start = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect");

        // Private hosts are allowed for the first hop here, so only the redirect check can reject.
        IiifRemoteUrlGuard permissiveFirstHop = new IiifRemoteUrlGuard(true) {
            @Override
            public void assertAllowed(URI uri) throws java.io.IOException {
                if (!uri.equals(start)) {
                    new IiifRemoteUrlGuard(false).assertAllowed(uri);
                }
            }
        };

        assertThatThrownBy(() -> permissiveFirstHop.send(
                IiifRemoteUrlGuard.clientBuilder().build(),
                start,
                uri -> HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
