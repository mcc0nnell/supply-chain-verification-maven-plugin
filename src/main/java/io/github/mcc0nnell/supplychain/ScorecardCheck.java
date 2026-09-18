package io.github.mcc0nnell.supplychain;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScorecardCheck implements EvidenceCheck {
    private static final URI API =
        URI.create("https://api.securityscorecards.dev/projects/");
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final JsonFactory JSON = new JsonFactory();

    private final ScmResolver resolver;
    private final Fetcher fetcher;
    private final double minimumScore;
    private final boolean offline;

    ScorecardCheck(
        ScmResolver resolver,
        Duration timeout,
        double minimumScore,
        boolean offline) {
        this(resolver, new HttpFetcher(timeout), minimumScore, offline);
    }

    ScorecardCheck(
        ScmResolver resolver,
        Fetcher fetcher,
        double minimumScore,
        boolean offline) {
        this.resolver = resolver;
        this.fetcher = fetcher;
        this.minimumScore = minimumScore;
        this.offline = offline;
    }

    @Override
    public String id() {
        return "openssf-scorecard-current";
    }

    @Override
    public Evidence inspect(ResolvedComponent component) {
        ScmResolver.Resolution resolution = resolver.resolve(component);
        if (!resolution.resolved()) {
            return new Evidence(
                id(),
                Evidence.Status.UNKNOWN,
                resolution.summary(),
                resolution.locations(),
                Map.of("scope", "current-repository-posture"));
        }

        URI endpoint = API.resolve(resolution.scorecardProject());
        List<String> locations = locations(resolution, endpoint);
        if (offline) {
            return new Evidence(
                id(),
                Evidence.Status.UNKNOWN,
                "Maven is offline; OpenSSF Scorecard lookup was not attempted",
                locations,
                Map.of(
                    "scope", "current-repository-posture",
                    "project", resolution.scorecardProject()));
        }

        try {
            FetchResult response = fetcher.fetch(endpoint);
            if (response.statusCode() == 404 || response.statusCode() == 410) {
                return new Evidence(
                    id(),
                    Evidence.Status.FAIL,
                    "current OpenSSF Scorecard result is not published for the SCM-associated repository",
                    locations,
                    Map.of(
                        "scope", "current-repository-posture",
                        "project", resolution.scorecardProject()));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new Evidence(
                    id(),
                    Evidence.Status.UNKNOWN,
                    "OpenSSF Scorecard lookup returned HTTP " + response.statusCode(),
                    locations,
                    Map.of(
                        "scope", "current-repository-posture",
                        "project", resolution.scorecardProject()));
            }

            ScorecardDocument document = parse(response.body());
            if (document.score() == null) {
                return new Evidence(
                    id(),
                    Evidence.Status.UNKNOWN,
                    "OpenSSF Scorecard response did not contain a top-level numeric score",
                    locations,
                    Map.of(
                        "scope", "current-repository-posture",
                        "project", resolution.scorecardProject()));
            }
            if (document.repoName() != null
                && !resolution.scorecardProject().equalsIgnoreCase(document.repoName())) {
                return new Evidence(
                    id(),
                    Evidence.Status.UNKNOWN,
                    "OpenSSF Scorecard response repository did not match the requested project",
                    locations,
                    Map.of(
                        "scope", "current-repository-posture",
                        "project", resolution.scorecardProject(),
                        "responseProject", document.repoName()));
            }

            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("scope", "current-repository-posture");
            attributes.put("project", resolution.scorecardProject());
            attributes.put("artifactVersionBound", "false");
            if (document.date() != null) {
                attributes.put("date", document.date());
            }
            if (document.commit() != null) {
                attributes.put("commit", document.commit());
            }

            String detail = "current OpenSSF Scorecard " + format(document.score())
                + " for " + resolution.scorecardProject();
            if (document.date() != null) {
                detail += " (" + document.date() + ")";
            }
            if (document.commit() != null) {
                detail += " at " + abbreviate(document.commit());
            }

            if (minimumScore >= 0 && document.score() < minimumScore) {
                return new Evidence(
                    id(),
                    Evidence.Status.FAIL,
                    detail + " is below required minimum " + format(minimumScore),
                    locations,
                    Map.copyOf(attributes));
            }

            return new Evidence(
                id(),
                Evidence.Status.PASS,
                detail + "; this is repository posture, not artifact-version provenance",
                locations,
                Map.copyOf(attributes));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unknown("OpenSSF Scorecard lookup interrupted", locations, resolution);
        } catch (IOException | RuntimeException e) {
            return unknown("OpenSSF Scorecard lookup was not conclusive", locations, resolution);
        }
    }

    private Evidence unknown(
        String summary,
        List<String> locations,
        ScmResolver.Resolution resolution) {
        return new Evidence(
            id(),
            Evidence.Status.UNKNOWN,
            summary,
            locations,
            Map.of(
                "scope", "current-repository-posture",
                "project", resolution.scorecardProject()));
    }

    private static List<String> locations(
        ScmResolver.Resolution resolution,
        URI endpoint) {

        List<String> locations = new ArrayList<>(resolution.locations());
        locations.add("https://" + resolution.scorecardProject());
        locations.add(endpoint.toString());
        return List.copyOf(locations);
    }

    static ScorecardDocument parse(byte[] body) throws IOException {
        Double score = null;
        String date = null;
        String repoName = null;
        String commit = null;

        try (JsonParser parser = JSON.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return new ScorecardDocument(null, null, null, null);
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if ("score".equals(field) && value != null && value.isNumeric()) {
                    score = parser.getDoubleValue();
                } else if ("date".equals(field) && value == JsonToken.VALUE_STRING) {
                    date = parser.getValueAsString();
                } else if ("repo".equals(field) && value == JsonToken.START_OBJECT) {
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String repoField = parser.currentName();
                        JsonToken repoValue = parser.nextToken();
                        if ("name".equals(repoField) && repoValue == JsonToken.VALUE_STRING) {
                            repoName = parser.getValueAsString();
                        } else if ("commit".equals(repoField) && repoValue == JsonToken.VALUE_STRING) {
                            commit = parser.getValueAsString();
                        } else {
                            parser.skipChildren();
                        }
                    }
                } else {
                    parser.skipChildren();
                }
            }
        }
        return new ScorecardDocument(score, date, repoName, commit);
    }

    private static String format(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    private static String abbreviate(String commit) {
        return commit.length() <= 12 ? commit : commit.substring(0, 12);
    }

    record ScorecardDocument(Double score, String date, String repoName, String commit) {}

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
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        }

        @Override
        public FetchResult fetch(URI uri) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "supply-chain-verification-maven-plugin/0.4")
                .GET()
                .build();
            HttpResponse<InputStream> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new IOException(
                        "OpenSSF Scorecard response exceeds " + MAX_RESPONSE_BYTES + " bytes");
                }
                return new FetchResult(response.statusCode(), bytes);
            }
        }
    }
}
