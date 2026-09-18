package io.github.mcc0nnell.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SbomCheckTest {
    @Test
    void passesWhenCycloneDxJsonResolves() {
        var check = new SbomCheck((component, extension, classifier) ->
            "json".equals(extension) && "cyclonedx".equals(classifier)
                ? found()
                : ResolvedArtifact.missing("not found"));

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.PASS, evidence.status());
        assertEquals(
            "CycloneDX JSON published in Maven resolution context",
            evidence.summary());
        assertEquals(1, evidence.locations().size());
    }

    @Test
    void triesLaterFormatsAfterMissingCandidate() {
        var calls = new AtomicInteger();
        var check = new SbomCheck((component, extension, classifier) -> {
            calls.incrementAndGet();
            return "xml".equals(extension)
                ? found()
                : ResolvedArtifact.missing("not found");
        });

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.PASS, evidence.status());
        assertEquals(2, calls.get());
    }

    @Test
    void failsWhenAllSupportedArtifactsAreAbsent() {
        var calls = new AtomicInteger();
        var check = new SbomCheck((component, extension, classifier) -> {
            calls.incrementAndGet();
            return ResolvedArtifact.missing("not found");
        });

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.FAIL, evidence.status());
        assertEquals(3, calls.get());
        assertEquals(
            "no supported SBOM artifact found in configured Maven repositories",
            evidence.summary());
    }

    @Test
    void remainsUnknownWhenResolverCannotConclude() {
        var check = new SbomCheck((component, extension, classifier) ->
            ResolvedArtifact.unknown("offline"));

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertEquals(
            "SBOM publication could not be determined in Maven resolution context",
            evidence.summary());
    }

    @Test
    void incompleteCoordinatesRemainUnknownWithoutResolution() {
        var calls = new AtomicInteger();
        var check = new SbomCheck((component, extension, classifier) -> {
            calls.incrementAndGet();
            return found();
        });

        var evidence = check.inspect(new Coordinate(
            "org.example", "demo", null, Coordinate.Kind.DEPENDENCY));

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertEquals(0, calls.get());
    }

    @Test
    void emitsLogicalCandidateCoordinatesDeterministically() {
        var check = new SbomCheck((component, extension, classifier) ->
            ResolvedArtifact.missing("not found"));

        var candidates = check.candidates(component());

        assertEquals(
            "org.example:demo:json:cyclonedx:1.2.3",
            candidates.get(0));
        assertEquals(
            "org.example:demo:xml:cyclonedx:1.2.3",
            candidates.get(1));
        assertEquals(
            "org.example:demo:json:spdx:1.2.3",
            candidates.get(2));
    }

    private static ResolvedArtifact found() {
        return ResolvedArtifact.found(
            Path.of("/resolver/demo"), "fixture", "abc123");
    }

    private static Coordinate component() {
        return new Coordinate(
            "org.example", "demo", "1.2.3", Coordinate.Kind.DEPENDENCY);
    }
}
