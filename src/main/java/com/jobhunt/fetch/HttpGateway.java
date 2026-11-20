package com.jobhunt.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** The only class that talks HTTP to career boards. Parsers never see a socket, so they test offline. */
@Component
public class HttpGateway {

    private static final String USER_AGENT = "jobhunt/1.0 (personal job search)";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper;

    public HttpGateway(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** GET and parse JSON. One retry, because boards time out now and then. */
    public JsonNode getJson(String url) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), 2);
    }

    public JsonNode postJson(String url, Object body) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return send(b, 2);
    }

    private JsonNode send(HttpRequest.Builder builder, int tries) throws IOException {
        HttpRequest req = builder.timeout(TIMEOUT).header("User-Agent", USER_AGENT)
                .header("Accept", "application/json").build();
        IOException last = null;
        for (int i = 0; i < tries; i++) {
            try {
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new HttpStatusException(res.statusCode());
                }
                return mapper.readTree(res.body());
            } catch (HttpStatusException e) {
                throw e;                      // a real answer: retrying will not change it
            } catch (IOException e) {
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
            sleepQuietly(1500);
        }
        throw last;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class HttpStatusException extends IOException {
        private final int status;

        public HttpStatusException(int status) {
            super("HTTP " + status);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
