package io.github.mcc0nnell.supplychain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

final class MavenEvidenceResolver {
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> dependencyRepositories;
    private final List<RemoteRepository> pluginRepositories;

    MavenEvidenceResolver(
        RepositorySystem repositorySystem,
        RepositorySystemSession session,
        List<RemoteRepository> dependencyRepositories,
        List<RemoteRepository> pluginRepositories) {

        this.repositorySystem = repositorySystem;
        this.session = session;
        this.dependencyRepositories = List.copyOf(dependencyRepositories);
        this.pluginRepositories = List.copyOf(pluginRepositories);
    }

    Coordinate enrich(Coordinate component) {
        ResolvedArtifact artifact = resolve(
            component,
            normalizedExtension(component.extension()),
            normalizedClassifier(component.classifier()));
        ResolvedArtifact pom = resolve(component, "pom", "");
        return component.withResolution(artifact, pom);
    }

    ResolvedArtifact resolvePom(Coordinate component) {
        if (component.pom() != null) {
            return component.pom();
        }
        return resolve(component, "pom", "");
    }

    ResolvedArtifact resolve(
        Coordinate component,
        String extension,
        String classifier) {

        if (incomplete(component)) {
            return ResolvedArtifact.unknown("component coordinates are incomplete");
        }

        var artifact = new DefaultArtifact(
            component.groupId(),
            component.artifactId(),
            normalizedClassifier(classifier),
            normalizedExtension(extension),
            component.version());

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(artifact);
        request.setRepositories(repositories(component));

        try {
            ArtifactResult result =
                repositorySystem.resolveArtifact(session, request);
            if (!result.isResolved() || result.getArtifact().getFile() == null) {
                return session.isOffline()
                    ? ResolvedArtifact.unknown(
                        "artifact is not available in the local repository while Maven is offline")
                    : ResolvedArtifact.missing(
                        "artifact was not found in configured Maven repositories");
            }

            Path file = result.getArtifact().getFile().toPath();
            return ResolvedArtifact.found(
                file,
                repositoryId(result.getRepository()),
                sha256(file));
        } catch (ArtifactResolutionException e) {
            if (session.isOffline()) {
                return ResolvedArtifact.unknown(
                    "artifact is not available in the local repository while Maven is offline");
            }
            boolean missing = !e.getResults().isEmpty()
                && e.getResults().stream().allMatch(ArtifactResult::isMissing);
            return missing
                ? ResolvedArtifact.missing(
                    "artifact was not found in configured Maven repositories")
                : ResolvedArtifact.unknown(
                    "Maven Resolver could not conclusively resolve artifact: "
                        + rootMessage(e));
        } catch (RuntimeException | IOException e) {
            return ResolvedArtifact.unknown(
                "Maven Resolver could not conclusively resolve artifact: "
                    + rootMessage(e));
        }
    }

    boolean offline() {
        return session.isOffline();
    }

    byte[] read(ResolvedArtifact artifact) throws IOException {
        if (artifact == null || !artifact.found() || artifact.file() == null) {
            throw new IOException("artifact is not resolved");
        }
        return Files.readAllBytes(artifact.file());
    }

    private List<RemoteRepository> repositories(Coordinate component) {
        return component.kind() == Coordinate.Kind.BUILD_PLUGIN
            ? pluginRepositories
            : dependencyRepositories;
    }

    private static boolean incomplete(Coordinate component) {
        return component.groupId() == null || component.groupId().isBlank()
            || component.artifactId() == null || component.artifactId().isBlank()
            || component.version() == null || component.version().isBlank();
    }

    private static String normalizedExtension(String extension) {
        return extension == null || extension.isBlank() ? "jar" : extension;
    }

    private static String normalizedClassifier(String classifier) {
        return classifier == null ? "" : classifier;
    }

    private static String repositoryId(ArtifactRepository repository) {
        return repository == null ? null : repository.getId();
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
            ? current.getClass().getSimpleName()
            : message;
    }
}
