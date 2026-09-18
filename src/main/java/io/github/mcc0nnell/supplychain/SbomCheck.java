package io.github.mcc0nnell.supplychain;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class SbomCheck implements EvidenceCheck {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final URI repository;
    private final Probe probe;

    SbomCheck(URI repository) {
        this(repository, new HttpProbe(DEFAULT_TIMEOUT));
    }

    SbomCheck(URI repository, Duration timeout) {
        this(repository, new HttpProbe(timeout));
    }

    SbomCheck(URI repository, Probe probe) {
        this.repository = repository;
        this.probe = probe;
    }

    @Override
    public String id() {
        return "public-sbom";
    }

    @Override
    public Evidence inspect(Coordinate component) {
        if (component.groupId() == null || component.groupId().isBlank()
            || component.artifactId() == null || component.artifactId().isBlank()
            || component.version() == null || component.version().isBlank()) {
            return new Evidence(
                id(),
                Evidence.Status.UNKNOWN,
                "component coordinates are incomplete",
                List.of());
        }

        List<String> candidates = candidates(component);
        boolean uncertain = false;

        for (String location : candidates) {
            try {
                ProbeResult result = probe.inspect(URI.create(location));
                if (result.found()) {
                    return new Evidence(
                        id(),
                        Evidence.Status.PASS,
                        "public SBOM published",
                        List.of(location));
                }
                if (!result.missing()) {
                    uncertain = true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Evidence(
                    id(),
                    Evidence.Status.UNKNOWN,
                    "SBOM lookup interrupted",
                    candidates);
            } catch (IOException | RuntimeException e) {
                uncertain = true;
            }
        }

        if (uncertain) {
            return new Evidence(
                id(),
                Evidence.Status.UNKNOWN,
                "public SBOM lookup was not conclusive",
                candidates);
        }

        return new Evidence(
            id(),
            Evidence.Status.FAIL,
            "no public SBOM found at known Maven repository locations",
            candidates);
    }

    List<String> candidates(Coordinate component) {
        String stem = repository.toString().replaceAll("/+$", "") + "/"
            + component.groupId().replace('.', '/') + "/"
            + component.artifactId() + "/"
            + component.version() + "/"
            + component.artifactId() + "-" + component.version();

        List<String> candidates = new ArrayList<>(3);
        candidates.add(stem + "-cyclonedx.json");
        candidates.add(stem + "-cyclonedx.xml");
        candidates.add(stem + ".spdx.json");
        return List.copyOf(candidates);
    }

    @FunctionalInterface
    interface Probe {
        ProbeResult inspect(URI uri) throws IOException, InterruptedException;
    }

    record ProbeResult(int statusCode) {
        boolean found() {
            return statusCode >= 200 && statusCode < 300;
        }

        boolean missing() {
            return statusCode == 404 || statusCode == 410;
        }
    }

    private static final class HttpProbe implements Probe {
        private final HttpClient client;
        private final Duration timeout;

        private HttpProbe(Duration timeout) {
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
        public ProbeResult inspect(URI uri) throws IOException, InterruptedException {
            HttpResponse<Void> response = client.send(
                request(uri, "HEAD"),
                HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() == 405 || response.statusCode() == 501) {
                response = client.send(
                    request(uri, "GET"),
                    HttpResponse.BodyHandlers.discarding());
            }
            return new ProbeResult(response.statusCode());
        }

        private HttpRequest request(URI uri, String method) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", "supply-chain-verification-maven-plugin/0.2")
                .method(method, HttpRequest.BodyPublishers.noBody());
            if ("GET".equals(method)) {
                builder.header("Range", "bytes=0-0");
            }
            return builder.build();
        }
    }
}
