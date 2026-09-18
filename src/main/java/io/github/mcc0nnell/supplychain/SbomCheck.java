package io.github.mcc0nnell.supplychain;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

final class SbomCheck implements EvidenceCheck {
    private final Probe probe;

    SbomCheck(RepositorySystem repositorySystem, RepositorySystemSession session) {
        this(new MavenResolverProbe(repositorySystem, session));
    }

    SbomCheck(Probe probe) {
        this.probe = probe;
    }

    @Override
    public String id() {
        return "public-sbom-sidecar";
    }

    @Override
    public Evidence inspect(ResolvedComponent component) {
        if (component.groupId() == null || component.groupId().isBlank()
            || component.artifactId() == null || component.artifactId().isBlank()
            || component.version() == null || component.version().isBlank()) {
            return unknown("component coordinates are incomplete", List.of());
        }
        if (component.artifactRepository() == null
            || component.artifactRepositoryUri() == null) {
            return unknown(
                "resolved artifact repository is unavailable; SBOM lookup cannot be bound to the consumed artifact",
                List.of());
        }
        if (component.hasClassifier()) {
            return unknown(
                "SBOM sidecar convention is not defined for classified artifacts",
                List.of(component.artifactRepositoryUri().toString()));
        }
        if (component.version().endsWith("-SNAPSHOT")) {
            return unknown(
                "SBOM sidecar convention is not defined for mutable SNAPSHOT coordinates",
                List.of(component.artifactRepositoryUri().toString()));
        }

        List<Sidecar> sidecars = sidecars(component);
        boolean uncertain = false;

        for (Sidecar sidecar : sidecars) {
            ProbeResult result = probe.inspect(component, sidecar);
            if (result.found()) {
                return new Evidence(
                    id(),
                    Evidence.Status.PASS,
                    "public SBOM sidecar resolved through Maven; content and artifact binding are not yet validated",
                    List.of(sidecar.location()),
                    Map.of(
                        "claim", "sidecar-exists",
                        "contentValidated", "false",
                        "resolution", "maven-resolver"));
            }
            if (!result.missing()) {
                uncertain = true;
            }
        }

        List<String> locations = sidecars.stream().map(Sidecar::location).toList();
        if (uncertain) {
            return unknown("public SBOM sidecar lookup was not conclusive", locations);
        }

        return new Evidence(
            id(),
            Evidence.Status.FAIL,
            "no public SBOM sidecar resolved at known locations in the artifact's Maven repository",
            locations,
            Map.of(
                "claim", "sidecar-exists",
                "contentValidated", "false",
                "resolution", "maven-resolver"));
    }

    List<Sidecar> sidecars(ResolvedComponent component) {
        String stem = component.artifactRepositoryUri().toString().replaceAll("/+$", "") + "/"
            + component.groupId().replace('.', '/') + "/"
            + component.artifactId() + "/"
            + component.version() + "/"
            + component.artifactId() + "-" + component.version();

        List<Sidecar> candidates = new ArrayList<>(3);
        candidates.add(new Sidecar(
            "cyclonedx",
            "json",
            stem + "-cyclonedx.json"));
        candidates.add(new Sidecar(
            "cyclonedx",
            "xml",
            stem + "-cyclonedx.xml"));
        candidates.add(new Sidecar(
            "",
            "spdx.json",
            stem + ".spdx.json"));
        return List.copyOf(candidates);
    }

    private Evidence unknown(String summary, List<String> locations) {
        return new Evidence(
            id(),
            Evidence.Status.UNKNOWN,
            summary,
            locations,
            Map.of(
                "claim", "sidecar-exists",
                "contentValidated", "false",
                "resolution", "maven-resolver"));
    }

    record Sidecar(String classifier, String extension, String location) {}

    @FunctionalInterface
    interface Probe {
        ProbeResult inspect(ResolvedComponent component, Sidecar sidecar);
    }

    record ProbeResult(State state) {
        enum State { FOUND, MISSING, UNKNOWN }

        static ProbeResult foundResult() {
            return new ProbeResult(State.FOUND);
        }

        static ProbeResult missingResult() {
            return new ProbeResult(State.MISSING);
        }

        static ProbeResult unknownResult() {
            return new ProbeResult(State.UNKNOWN);
        }

        boolean found() {
            return state == State.FOUND;
        }

        boolean missing() {
            return state == State.MISSING;
        }
    }

    private static final class MavenResolverProbe implements Probe {
        private final RepositorySystem repositorySystem;
        private final RepositorySystemSession session;

        private MavenResolverProbe(
            RepositorySystem repositorySystem,
            RepositorySystemSession session) {
            this.repositorySystem = repositorySystem;
            this.session = session;
        }

        @Override
        public ProbeResult inspect(ResolvedComponent component, Sidecar sidecar) {
            RemoteRepository repository = component.artifactRepository();
            if (repository == null) {
                return ProbeResult.unknownResult();
            }

            var artifact = new DefaultArtifact(
                component.groupId(),
                component.artifactId(),
                sidecar.classifier(),
                sidecar.extension(),
                component.baseVersion() == null
                    ? component.version()
                    : component.baseVersion());

            ArtifactRequest request = new ArtifactRequest(
                artifact,
                List.of(repository),
                "supply-chain-verification");

            try {
                ArtifactResult result = repositorySystem.resolveArtifact(session, request);
                return result.isResolved()
                    ? ProbeResult.foundResult()
                    : result.isMissing()
                        ? ProbeResult.missingResult()
                        : ProbeResult.unknownResult();
            } catch (ArtifactResolutionException e) {
                ArtifactResult result = e.getResult();
                if (session.isOffline()) {
                    return ProbeResult.unknownResult();
                }
                return result != null && result.isMissing()
                    ? ProbeResult.missingResult()
                    : ProbeResult.unknownResult();
            } catch (RuntimeException e) {
                return ProbeResult.unknownResult();
            }
        }
    }
}
