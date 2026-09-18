package io.github.mcc0nnell.supplychain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class ScmResolver {
    private static final long MAX_POM_BYTES = 2L * 1024 * 1024;

    private final PomSource source;

    ScmResolver(Path localRepository) {
        this(new LocalPomSource(localRepository));
    }

    ScmResolver(PomSource source) {
        this.source = source;
    }

    Resolution resolve(ResolvedComponent component) {
        return resolve(component, 0);
    }

    private Resolution resolve(ResolvedComponent component, int depth) {
        if (component.groupId() == null || component.groupId().isBlank()
            || component.artifactId() == null || component.artifactId().isBlank()
            || component.version() == null || component.version().isBlank()) {
            return Resolution.unknown("component coordinates are incomplete", List.of());
        }
        if (depth > 4) {
            return Resolution.unknown("SCM parent resolution depth exceeded", List.of());
        }
        if (depth == 0) {
            if (component.artifactRepositoryId() == null || component.pomRepositoryId() == null) {
                return Resolution.unknown(
                    "artifact/POM source provenance is unavailable; SCM association is not trusted",
                    sourceLocations(component));
            }
            if (!component.artifactAndPomSourcesAgree()) {
                return Resolution.unknown(
                    "artifact and POM were resolved from different repositories; SCM association is not trusted",
                    sourceLocations(component));
            }
        }

        try {
            PomResult result = source.read(component);
            if (!result.found()) {
                return Resolution.unknown(
                    "Maven-resolved POM not found in the local repository",
                    List.of(result.location()));
            }

            List<String> candidates = scmCandidates(result.body());
            for (String candidate : candidates) {
                Optional<String> canonical = canonicalize(candidate);
                if (canonical.isPresent()) {
                    return Resolution.resolved(
                        canonical.get(),
                        "source repository associated by SCM metadata in Maven-resolved POM",
                        List.of(result.location(), candidate));
                }
            }

            if (candidates.isEmpty()) {
                Optional<ResolvedComponent> parent = parentCoordinate(result.body());
                if (parent.isPresent()) {
                    Resolution inherited = resolve(parent.get(), depth + 1);
                    List<String> inheritedLocations = new ArrayList<>();
                    inheritedLocations.add(result.location());
                    inheritedLocations.addAll(inherited.locations());
                    return inherited.resolved()
                        ? Resolution.resolved(
                            inherited.scorecardProject(),
                            "source repository inherited from parent SCM metadata in Maven-resolved POM",
                            List.copyOf(inheritedLocations))
                        : Resolution.unknown(
                            inherited.summary(),
                            List.copyOf(inheritedLocations));
                }
            }

            List<String> locations = new ArrayList<>();
            locations.add(result.location());
            locations.addAll(candidates);
            return Resolution.unknown(
                candidates.isEmpty()
                    ? "Maven-resolved POM does not declare SCM metadata"
                    : "SCM metadata is not a supported public Git repository",
                List.copyOf(locations));
        } catch (Exception e) {
            return Resolution.unknown(
                "SCM lookup was not conclusive: " + e.getClass().getSimpleName(),
                List.of("maven-local:" + component.gav() + ":pom"));
        }
    }

    private static List<String> sourceLocations(ResolvedComponent component) {
        List<String> locations = new ArrayList<>();
        if (component.artifactRepositoryId() != null) {
            locations.add("artifact-repository:" + component.artifactRepositoryId());
        }
        if (component.pomRepositoryId() != null) {
            locations.add("pom-repository:" + component.pomRepositoryId());
        }
        return List.copyOf(locations);
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
        Element root = parse(pom);
        Element scm = directChild(root, "scm");
        if (scm == null) {
            return List.of();
        }

        List<String> candidates = new ArrayList<>(3);
        for (String name : List.of("url", "connection", "developerConnection")) {
            Element node = directChild(scm, name);
            if (node != null) {
                String value = node.getTextContent();
                if (value != null && !value.isBlank()) {
                    candidates.add(value.trim());
                }
            }
        }
        return List.copyOf(candidates);
    }

    static Optional<ResolvedComponent> parentCoordinate(byte[] pom) throws Exception {
        Element root = parse(pom);
        Element parent = directChild(root, "parent");
        if (parent == null) {
            return Optional.empty();
        }

        String groupId = childText(parent, "groupId");
        String artifactId = childText(parent, "artifactId");
        String version = childText(parent, "version");
        if (groupId == null || artifactId == null || version == null
            || groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new ResolvedComponent(
            groupId.trim(),
            artifactId.trim(),
            version.trim(),
            ResolvedComponent.Kind.DEPENDENCY));
    }

    private static Element parse(byte[] pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(pom))
            .getDocumentElement();
    }

    private static Element directChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static String childText(Element parent, String name) {
        Element child = directChild(parent, name);
        return child == null ? null : child.getTextContent();
    }

    @FunctionalInterface
    interface PomSource {
        PomResult read(ResolvedComponent component) throws IOException;
    }

    record PomResult(boolean found, byte[] body, String location) {}

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

    private static final class LocalPomSource implements PomSource {
        private final Path localRepository;

        private LocalPomSource(Path localRepository) {
            this.localRepository = localRepository;
        }

        @Override
        public PomResult read(ResolvedComponent component) throws IOException {
            String baseVersion = component.baseVersion() == null
                ? component.version()
                : component.baseVersion();
            Path pom = localRepository
                .resolve(component.groupId().replace('.', '/'))
                .resolve(component.artifactId())
                .resolve(baseVersion)
                .resolve(component.artifactId() + "-" + baseVersion + ".pom");

            String location = "maven-local:" + component.groupId() + ":"
                + component.artifactId() + ":" + baseVersion + ":pom";
            if (!Files.isRegularFile(pom)) {
                return new PomResult(false, new byte[0], location);
            }
            long size = Files.size(pom);
            if (size > MAX_POM_BYTES) {
                throw new IOException("POM exceeds " + MAX_POM_BYTES + " bytes");
            }
            return new PomResult(true, Files.readAllBytes(pom), location);
        }
    }
}
