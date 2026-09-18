package io.github.mcc0nnell.supplychain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class ScmResolver {
    private final PomFetcher fetcher;

    ScmResolver(MavenEvidenceResolver resolver) {
        this(component -> {
            ResolvedArtifact pom = resolver.resolvePom(component);
            if (!pom.found()) {
                return new FetchResult(
                    pom.state(), new byte[0], pom.location(), pom.summary());
            }
            return new FetchResult(
                ResolvedArtifact.State.FOUND,
                resolver.read(pom),
                pom.location(),
                pom.summary());
        });
    }

    ScmResolver(PomFetcher fetcher) {
        this.fetcher = fetcher;
    }

    Resolution resolve(Coordinate component) {
        return resolve(component, 0);
    }

    private Resolution resolve(Coordinate component, int depth) {
        if (component.groupId() == null || component.groupId().isBlank()
            || component.artifactId() == null || component.artifactId().isBlank()
            || component.version() == null || component.version().isBlank()) {
            return Resolution.unknown(
                "component coordinates are incomplete", List.of());
        }
        if (depth > 4) {
            return Resolution.unknown(
                "SCM parent resolution depth exceeded", List.of());
        }

        FetchResult result;
        try {
            result = fetcher.fetch(component);
        } catch (Exception e) {
            return Resolution.unknown(
                "SCM POM resolution was not conclusive: "
                    + e.getClass().getSimpleName(),
                List.of(component.gav()));
        }

        String pomLocation = result.location() == null
            ? component.gav() + ":pom"
            : result.location();

        if (result.state() == ResolvedArtifact.State.MISSING) {
            return Resolution.unknown(
                "published POM not found in Maven resolution context",
                List.of(pomLocation));
        }
        if (result.state() != ResolvedArtifact.State.FOUND) {
            return Resolution.unknown(
                result.summary() == null
                    ? "published POM resolution was not conclusive"
                    : result.summary(),
                List.of(pomLocation));
        }

        try {
            List<String> candidates = scmCandidates(result.body());
            for (String candidate : candidates) {
                Optional<String> canonical = canonicalize(candidate);
                if (canonical.isPresent()) {
                    return Resolution.resolved(
                        canonical.get(),
                        "canonical source repository resolved from Maven-resolved POM",
                        List.of(pomLocation, candidate));
                }
            }

            if (candidates.isEmpty()) {
                Optional<Coordinate> parent =
                    parentCoordinate(result.body(), component.kind());
                if (parent.isPresent()) {
                    Resolution inherited = resolve(parent.get(), depth + 1);
                    List<String> locations = new ArrayList<>();
                    locations.add(pomLocation);
                    locations.addAll(inherited.locations());
                    return inherited.resolved()
                        ? Resolution.resolved(
                            inherited.scorecardProject(),
                            "canonical source repository inherited from Maven-resolved parent POM",
                            List.copyOf(locations))
                        : Resolution.unknown(
                            inherited.summary(),
                            List.copyOf(locations));
                }
            }

            List<String> locations = new ArrayList<>();
            locations.add(pomLocation);
            locations.addAll(candidates);
            return Resolution.unknown(
                candidates.isEmpty()
                    ? "Maven-resolved POM does not declare SCM metadata"
                    : "SCM metadata is not a supported public Git repository",
                List.copyOf(locations));
        } catch (Exception e) {
            return Resolution.unknown(
                "SCM metadata could not be parsed: "
                    + e.getClass().getSimpleName(),
                List.of(pomLocation));
        }
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
            candidate = "https://github.com/"
                + candidate.substring("git@github.com:".length());
        } else if (candidate.startsWith("ssh://git@github.com/")) {
            candidate = "https://github.com/"
                + candidate.substring("ssh://git@github.com/".length());
        } else if (candidate.startsWith("git://github.com/")) {
            candidate = "https://github.com/"
                + candidate.substring("git://github.com/".length());
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
            return repo.isBlank()
                ? Optional.empty()
                : Optional.of("github.com/" + parts[0] + "/" + repo);
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
        return repo.isBlank()
            ? Optional.empty()
            : Optional.of("github.com/" + parts[0] + "/" + repo);
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
        Element root = parsePom(pom);
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

    static Optional<Coordinate> parentCoordinate(
        byte[] pom,
        Coordinate.Kind kind) throws Exception {

        Element root = parsePom(pom);
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
            kind,
            "pom",
            ""));
    }

    private static Element parsePom(byte[] pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(
            "http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(
            "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
            "http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(pom))
            .getDocumentElement();
    }

    private static String childText(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        return children.getLength() == 0
            ? null
            : children.item(0).getTextContent();
    }

    @FunctionalInterface
    interface PomFetcher {
        FetchResult fetch(Coordinate component) throws IOException;
    }
    record FetchResult(
        ResolvedArtifact.State state,
        byte[] body,
        String location,
        String summary) {}

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

        static Resolution unknown(
            String summary,
            List<String> locations) {
            return new Resolution(null, summary, locations);
        }

        boolean resolved() {
            return scorecardProject != null;
        }
    }
}
