package de.uniwue.zpd.dachs.larex.backend.service.action;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionPublicBaseUrlServiceTest {

    @Test
    void derivesAllowedCallbackBaseFromRequestHost() {
        ActionPublicBaseUrlService service = new ActionPublicBaseUrlService(
                "",
                "https://larex.example.org",
                true
        );
        MockHttpServletRequest request = request("https", "larex.example.org", 443);

        assertThat(service.publicApiBaseUrl(request)).isEqualTo("https://larex.example.org/api/v1");
    }

    @Test
    void rejectsDerivedCallbackBaseWhenHostIsNotAllowlisted() {
        ActionPublicBaseUrlService service = new ActionPublicBaseUrlService(
                "",
                "https://larex.example.org",
                true
        );
        MockHttpServletRequest request = request("https", "evil.example.org", 443);

        assertThatThrownBy(() -> service.publicApiBaseUrl(request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not allowlisted");
    }

    @Test
    void rejectsInsecureExternalDerivedCallbackBase() {
        ActionPublicBaseUrlService service = new ActionPublicBaseUrlService(
                "",
                "http://larex.example.org",
                true
        );
        MockHttpServletRequest request = request("http", "larex.example.org", 80);

        assertThatThrownBy(() -> service.publicApiBaseUrl(request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("https");
    }

    @Test
    void permitsInsecureLocalDevelopmentCallbackBase() {
        ActionPublicBaseUrlService service = new ActionPublicBaseUrlService(
                "",
                "http://larex.localhost",
                true
        );
        MockHttpServletRequest request = request("http", "larex.localhost", 80);

        assertThat(service.publicApiBaseUrl(request)).isEqualTo("http://larex.localhost/api/v1");
    }

    @Test
    void usesConfiguredCallbackBaseWithoutRequestHostAllowlist() {
        ActionPublicBaseUrlService service = new ActionPublicBaseUrlService(
                "http://app:8080/api/v1/",
                "https://larex.example.org",
                true
        );

        assertThat(service.publicApiBaseUrl(request("https", "ignored.example.org", 443)))
                .isEqualTo("http://app:8080/api/v1");
    }

    private MockHttpServletRequest request(String scheme, String host, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        request.setRequestURI("/api/v1/workspaces/ws/actions/projects/pr/runs");
        request.setContextPath("");
        return request;
    }
}
