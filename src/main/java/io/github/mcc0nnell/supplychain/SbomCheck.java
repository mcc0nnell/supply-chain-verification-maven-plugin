package io.github.mcc0nnell.supplychain;

import java.util.ArrayList;
import java.util.List;

final class SbomCheck implements EvidenceCheck {
    private static final List<Candidate> CANDIDATES = List.of(
        new Candidate("json", "cyclonedx", "CycloneDX JSON"),
        new Candidate("xml", "cyclonedx", "CycloneDX XML"),
        new Candidate("json", "spdx", "SPDX JSON"));

    private final Resolver resolver;

    SbomCheck(MavenEvidenceResolver resolver) {
        this(resolver::resolve);
    }

    SbomCheck(Resolver resolver) {
        this.resolver = resolver;
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

        List<String> attempted = new ArrayList<>(CANDIDATES.size());
        boolean uncertain = false;

        for (Candidate candidate : CANDIDATES) {
            ResolvedArtifact result = resolver.resolve(
                component, candidate.extension(), candidate.classifier());
            attempted.add(candidate.location(component, result));

            if (result.state() == ResolvedArtifact.State.FOUND) {
                return new Evidence(
                    id(),
                    Evidence.Status.PASS,
                    candidate.label() + " published in Maven resolution context",
                    List.of(candidate.location(component, result)));
            }
            if (result.state() == ResolvedArtifact.State.UNKNOWN) {
                uncertain = true;
            }
        }

        if (uncertain) {
            return new Evidence(
                id(),
                Evidence.Status.UNKNOWN,
                "SBOM publication could not be determined in Maven resolution context",
                List.copyOf(attempted));
        }

        return new Evidence(
            id(),
            Evidence.Status.FAIL,
            "no supported SBOM artifact found in configured Maven repositories",
            List.copyOf(attempted));
    }

    List<String> candidates(Coordinate component) {
        return CANDIDATES.stream()
            .map(candidate -> candidate.logicalCoordinates(component))
            .toList();
    }

    @FunctionalInterface
    interface Resolver {
        ResolvedArtifact resolve(
            Coordinate component,
            String extension,
            String classifier);
    }

    private record Candidate(
        String extension,
        String classifier,
        String label) {

        String logicalCoordinates(Coordinate component) {
            return component.groupId() + ":" + component.artifactId() + ":"
                + extension + ":" + classifier + ":" + component.version();
        }

        String location(Coordinate component, ResolvedArtifact result) {
            return logicalCoordinates(component)
                + " [" + result.state() + "] "
                + (result.found() ? result.location() : result.summary());
        }
    }
}
