package io.github.mcc0nnell.supplychain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class ScmResolver {
    private final URI repository;
    private final PomFetcher fetcher;

    ScmResolver(URI repository, Duration timeout) {
        this(repository, new HttpPomFetcher(timeout));
    }

    ScmResolver(URI repository, PomFetcher fetcher) {
        this.repository = repository;
        this.fetcher = fetcher;
    }

    Resolution resolve(Coordinate component) {
        return resolve(component, 0);
    }

    private Resolution resolve(Coordinate component, int depth) {
        if (component.groupId() == null || component.groupId().isBlank()
            || component.artifactId() == null || component.artifactId().isBlank()
            || component.version() == null || component.version().isBlank()) {
            return Resolution.unknown("component coordinates are incomplete", List.of());
        }
        if (depth > 4) {
            return Resolution.unknown("SCM parent resolution depth exceeded", List.of());
        }

        URI pom = pomUri(component);
        try {
            FetchResult result = fetcher.fetch(pom);
            if (result.statusCode() == 404 || result.statusCode() == 410) {
                return Resolution.unknown("published POM not found", List.of(pom.toString()));
            }
            if (result.statusCode() < 200 || result.statusCode() >= 300) {
                return Resolution.unknown(
                    "published POM lookup returned HTTP " + result.statusCode(),
                    List.of(pom.toString()));
            }

            List<String> candidates = scmCandidates(result.body());
            for (String candidate : candidates) {
                Optional<String> canonical = canonicalize(candidate);
                if (canonical.isPresent()) {
                    return Resolution.resolved(
                        canonical.get(),
                        "canonical source repository resolved from published POM",
                        List.of(pom.toString(), candidate));
                }
            }

            if (candidates.isEmpty()) {
                Optional<Coordinate> parent = parentCoordinate(result.body());
                if (parent.isPresent()) {
                    Resolution inherited = resolve(parent.get(), depth + 1);
                    List<String> inheritedLocations = new ArrayList<>();
                    inheritedLocations.add(pom.toString());
                    inheritedLocations.addAll(inherited.locations());
                    return inherited.resolved()
                        ? Resolution.resolved(
                            inherited.scorecardProject(),
                            "canonical source repository inherited from parent POM",
                            List.copyOf(inheritedLocations))
                        : Resolution.unknown(
                            inherited.summary(),
                            List.copyOf(inheritedLocations));
                }
            }

            List<String> locations = new ArrayList<>();
            locations.add(pom.toString());
            locations.addAll(candidates);
            return Resolution.unknown(
                candidates.isEmpty()
                    ? "published POM does not declare SCM metadata"
                    : "SCM metadata is not a supported public Git repository",
                List.copyOf(locations));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resolution.unknown("SCM lookup interrupted", List.of(pom.toString()));
        } catch (Exception e) {
            return Resolution.unknown(
                "SCM lookup was not conclusive: " + e.getClass().getSimpleName(),
                List.of(pom.toString()));
        }
    }

    URI pomUri(Coordinate component) {
        String base = repository.toString().replaceAll("/+$", "");
        return URI.create(base + "/"
            + component.groupId().replace('.', '/') + "/"
            + component.artifactId() + "/"
            + component.version() + "/"
            + component.artifactId() + "-" + component.version() + ".pom");
    }

    static Optional<String> canonicalize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String candidate = value.trim();

        for (String prefix : List.of("scm:git:", "scm:git|", "scm:")) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                candidate = candidate.substring(prefix.length());
                break;
            }
        }

        if (candidate.startsWith("git@github.com:")) {
            candidate = "https://github.com/" + candidate.substring("git@github.com:".length());
        } else if (candidate.startsWith("ssh://git@github.com/")) {
            candidate = "https://github.com/" + candidate.substring("ssh://git@github.com/".length());
        } else if (candidate.startsWith("git://github.com/")) {
            candidate = "https://github.com/" + candidate.substring("git://github.com/".length());
        }

        Optional<String> gitbox = canonicalizeApacheGitbox(candidate);
        if (gitbox.isPresent()) {
            return gitbox;
        }

        Optional<String> github = canonicalizeGithub(candidate);
        if (github.isPresent()) {
            return github;
        }

        try {
            URI uri = URI.create(candidate);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                return Optional.empty();
            }
            String path = uri.getPath();
            if (path == null) {
                return Optional.empty();
            }
            String[] parts = path.replaceAll("^/+", "").split("/");
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return Optional.empty();
            }
            String repo = parts[1].replaceFirst("\\.git$", "");
            if (repo.isBlank()) {
                return Optional.empty();
            }
            return Optional.of("github.com/" + parts[0] + "/" + repo);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> canonicalizeGithub(String candidate) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        String prefix;
        if (lower.startsWith("https://github.com/")) {
            prefix = "https://github.com/";
        } else if (lower.startsWith("http://github.com/")) {
            prefix = "http://github.com/";
        } else {
            return Optional.empty();
        }

        String rest = candidate.substring(prefix.length());
        String[] parts = rest.split("/", 3);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }

        String repo = parts[1]
            .replaceFirst("\\.git$", "")
            .replaceAll("[?#].*$", "");
        if (repo.isBlank()) {
            return Optional.empty();
        }
        return Optional.of("github.com/" + parts[0] + "/" + repo);
    }

    private static Optional<String> canonicalizeApacheGitbox(String candidate) {
        if (!candidate.contains("gitbox.apache.org")) {
            return Optional.empty();
        }

        try {
            URI uri = URI.create(candidate);
            String repo = null;
            if (uri.getQuery() != null) {
                for (String part : uri.getQuery().split("&")) {
                    if (part.startsWith("p=")) {
                        repo = part.substring(2);
                        break;
                    }
                }
            }
            if (repo == null && uri.getPath() != null) {
                String path = uri.getPath();
                int asf = path.indexOf("/asf/");
                if (asf >= 0) {
                    repo = path.substring(asf + "/asf/".length());
                }
            }
            if (repo == null || repo.isBlank()) {
                return Optional.empty();
            }
            repo = repo.replaceFirst("\\.git$", "").replaceAll("/+$", "");
            return repo.isBlank()
                ? Optional.empty()
                : Optional.of("github.com/apache/" + repo);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static List<String> scmCandidates(byte[] pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Element root = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(pom))
            .getDocumentElement();

        NodeList scmNodes = root.getElementsByTagName("scm");
        if (scmNodes.getLength() == 0) {
            return List.of();
        }

        Element scm = (Element) scmNodes.item(0);
        List<String> candidates = new ArrayList<>(3);
        for (String name : List.of("url", "connection", "developerConnection")) {
            NodeList nodes = scm.getElementsByTagName(name);
            if (nodes.getLength() > 0) {
                String value = nodes.item(0).getTextContent();
                if (value != null && !value.isBlank()) {
                    candidates.add(value.trim());
                }
            }
        }
        return List.copyOf(candidates);
    }

    static Optional<Coordinate> parentCoordinate(byte[] pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Element root = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(pom))
            .getDocumentElement();

        NodeList parents = root.getElementsByTagName("parent");
        if (parents.getLength() == 0) {
            return Optional.empty();
        }

        Element parent = (Element) parents.item(0);
        String groupId = childText(parent, "groupId");
        String artifactId = childText(parent, "artifactId");
        String version = childText(parent, "version");
        if (groupId == null || artifactId == null || version == null
            || groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new Coordinate(
            groupId.trim(),
            artifactId.trim(),
            version.trim(),
            Coordinate.Kind.DEPENDENCY));
    }

    private static String childText(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        if (children.getLength() == 0) {
            return null;
        }
        return children.item(0).getTextContent();
    }

    @FunctionalInterface
    interface PomFetcher {
        FetchResult fetch(URI uri) throws IOException, InterruptedException;
    }

    record FetchResult(int statusCode, byte[] body) {}

    record Resolution(
        String scorecardProject,
        String summary,
        List<String> locations) {

        static Resolution resolved(
            String scorecardProject,
            String summary,
            List<String> locations) {
            return new Resolution(scorecardProject, summary, locations);
        }

        static Resolution unknown(String summary, List<String> locations) {
            return new Resolution(null, summary, locations);
        }

        boolean resolved() {
            return scorecardProject != null;
        }
    }

    private static final class HttpPomFetcher implements PomFetcher {
        private final HttpClient client;
        private final Duration timeout;

        private HttpPomFetcher(Duration timeout) {
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
