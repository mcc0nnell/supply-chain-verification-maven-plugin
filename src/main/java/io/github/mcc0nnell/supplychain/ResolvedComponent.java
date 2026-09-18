package io.github.mcc0nnell.supplychain;

import java.net.URI;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Identity and local provenance of the artifact Maven actually resolved for this build.
 */
record ResolvedComponent(
    String groupId,
    String artifactId,
    String version,
    String baseVersion,
    String type,
    String classifier,
    String extension,
    Kind kind,
    URI artifactRepositoryUri,
    String artifactRepositoryId,
    URI pomRepositoryUri,
    String pomRepositoryId,
    String sha256,
    String pomSha256,
    RemoteRepository artifactRepository) {

    enum Kind { DEPENDENCY, BUILD_PLUGIN }

    ResolvedComponent(
        String groupId,
        String artifactId,
        String version,
        Kind kind) {
        this(
            groupId,
            artifactId,
            version,
            version,
            "jar",
            null,
            "jar",
            kind,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    ResolvedComponent(
        String groupId,
        String artifactId,
        String version,
        String baseVersion,
        String type,
        String classifier,
        String extension,
        Kind kind,
        URI repositoryUri,
        String repositoryId,
        String sha256) {
        this(
            groupId,
            artifactId,
            version,
            baseVersion,
            type,
            classifier,
            extension,
            kind,
            repositoryUri,
            repositoryId,
            repositoryUri,
            repositoryId,
            sha256,
            null,
            repositoryUri == null || repositoryId == null
                ? null
                : new RemoteRepository.Builder(
                    repositoryId,
                    "default",
                    repositoryUri.toString()).build());
    }

    String gav() {
        return groupId + ":" + artifactId + ":" + (version == null ? "UNKNOWN" : version);
    }

    boolean hasClassifier() {
        return classifier != null && !classifier.isBlank();
    }

    boolean artifactAndPomSourcesAgree() {
        return artifactRepositoryId != null
            && pomRepositoryId != null
            && artifactRepositoryId.equals(pomRepositoryId);
    }
}
