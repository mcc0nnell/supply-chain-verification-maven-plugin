package io.github.mcc0nnell.supplychain;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScorecardCheck implements EvidenceCheck {
    private static final URI API =
        URI.create("https://api.securityscorecards.dev/projects/");
    private static final Pattern SCORE =
        Pattern.compile("\\\"score\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern DATE =
        Pattern.compile("\\\"date\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final ScmResolver resolver;
    private final Fetcher fetcher;
    private final double minimumScore;

    ScorecardCheck(ScmResolver resolver, Duration timeout, double minimumScore) {
        this(resolver, new HttpFetcher(timeout), minimumScore);
    }

    ScorecardCheck(ScmResolver resolver, Fetcher fetcher, double minimumScore) {
        this.resolver = resolver;
        this.fetcher = fetcher;
        this.minimumScore = minimumScore;
    }

    @Override
    public String id() {
        return "openssf-scorecard";
    }

    @Override
    public Evidence inspect(Coordinate component) {
        ScmResolver.Resolution resolution = resolver.resolve(component);
        if (!resolution.resolved()) {
            return new Evidence(
                id(),
                Evidence.Status.UNKNOWN,
                resolution.summary(),
                resolution.locations());
        }

        URI endpoint = API.resolve(resolution.scorecardProject());
        List<String> locations = locations(resolution, endpoint);

        try {
            FetchResult response = fetcher.fetch(endpoint);
            if (response.statusCode() == 404 || response.statusCode() == 410) {
                return new Evidence(
                    id(),
                    Evidence.Status.FAIL,
                    "OpenSSF Scorecard result is not published",
                    locations);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new Evidence(
                    id(),
                    Evidence.Status.UNKNOWN,
                    "OpenSSF Scorecard lookup returned HTTP " + response.statusCode(),
                    locations);
            }

            String body = new String(response.body(), StandardCharsets.UTF_8);
            Matcher scoreMatch = SCORE.matcher(body);
            if (!scoreMatch.find()) {
                return new Evidence(
                    id(),
                    Evidence.Status.UNKNOWN,
                    "OpenSSF Scorecard response did not contain a numeric score",
                    locations);
            }

            double score = Double.parseDouble(scoreMatch.group(1));
            String date = match(DATE, body);
            String detail = date == null
                ? "OpenSSF Scorecard " + format(score)
                : "OpenSSF Scorecard " + format(score) + " (" + date + ")";

            if (minimumScore >= 0 && score < minimumScore) {
                return new Evidence(
                    id(),
                    Evidence.Status.FAIL,
                    detail + " is below required minimum " + format(minimumScore),
                    locations);
            }

            return new Evidence(
                id(),
                Evidence.Status.PASS,
                detail,
                locations);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Evidence(
                id(),
                Evidence.Status.UNKNOWN,
                "OpenSSF Scorecard lookup interrupted",
                locations);
        } catch (IOException | RuntimeException e) {
            return new Evidence(
                id(),
                Evidence.Status.UNKNOWN,
                "OpenSSF Scorecard lookup was not conclusive",
                locations);
        }
    }

    private static List<String> locations(
        ScmResolver.Resolution resolution,
        URI endpoint) {

        List<String> locations = new ArrayList<>(resolution.locations());
        locations.add("https://" + resolution.scorecardProject());
        locations.add(endpoint.toString());
        return List.copyOf(locations);
    }

    private static String match(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String format(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    @FunctionalInterface
    interface Fetcher {
        FetchResult fetch(URI uri) throws IOException, InterruptedException;
    }

    record FetchResult(int statusCode, byte[] body) {}

    private static final class HttpFetcher implements Fetcher {
        private final HttpClient client;
        private final Duration timeout;

        private HttpFetcher(Duration timeout) {
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("request timeout must be positive");
            }
            this.timeout = timeout;
            this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        }

        @Override
        public FetchResult fetch(URI uri) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "supply-chain-verification-maven-plugin/0.3")
                .GET()
                .build();
            HttpResponse<byte[]> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray());
            return new FetchResult(response.statusCode(), response.body());
        }
    }
}
