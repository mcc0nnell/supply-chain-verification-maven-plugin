package io.github.mcc0nnell.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SbomCheckTest {
    private static final URI REPOSITORY =
        URI.create("https://repo.maven.apache.org/maven2");

    @Test
    void passesWhenPublishedCycloneDxJsonExists() {
        var check = new SbomCheck(REPOSITORY, uri ->
            new SbomCheck.ProbeResult(uri.toString().endsWith("-cyclonedx.json") ? 200 : 404));

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.PASS, evidence.status());
        assertEquals("public SBOM published", evidence.summary());
        assertEquals(1, evidence.locations().size());
        assertEquals(
            "https://repo.maven.apache.org/maven2/org/example/demo/1.2.3/demo-1.2.3-cyclonedx.json",
            evidence.locations().get(0));
    }

    @Test
    void triesLaterFormatsAfterMissingCandidate() {
        var calls = new AtomicInteger();
        var check = new SbomCheck(REPOSITORY, uri -> {
            calls.incrementAndGet();
            return new SbomCheck.ProbeResult(
                uri.toString().endsWith("-cyclonedx.xml") ? 200 : 404);
        });

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.PASS, evidence.status());
        assertEquals(2, calls.get());
        assertEquals(
            "https://repo.maven.apache.org/maven2/org/example/demo/1.2.3/demo-1.2.3-cyclonedx.xml",
            evidence.locations().get(0));
    }

    @Test
    void failsWhenAllKnownLocationsAreAbsent() {
        var calls = new AtomicInteger();
        var check = new SbomCheck(REPOSITORY, uri -> {
            calls.incrementAndGet();
            return new SbomCheck.ProbeResult(404);
        });

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.FAIL, evidence.status());
        assertEquals(3, calls.get());
        assertEquals(3, evidence.locations().size());
        assertEquals(
            "no public SBOM found at known Maven repository locations",
            evidence.summary());
    }

    @Test
    void remainsUnknownWhenRepositoryResponseIsTransient() {
        var check = new SbomCheck(REPOSITORY, uri ->
            new SbomCheck.ProbeResult(503));

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertEquals("public SBOM lookup was not conclusive", evidence.summary());
    }

    @Test
    void incompleteCoordinatesRemainUnknownWithoutNetworkAccess() {
        var calls = new AtomicInteger();
        var check = new SbomCheck(REPOSITORY, uri -> {
            calls.incrementAndGet();
            return new SbomCheck.ProbeResult(200);
        });

        var evidence = check.inspect(
            new Coordinate("org.example", "demo", null, Coordinate.Kind.DEPENDENCY));

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertEquals("component coordinates are incomplete", evidence.summary());
        assertEquals(0, calls.get());
    }

    @Test
    void emitsKnownCandidateLocationsDeterministically() {
        var check = new SbomCheck(REPOSITORY, uri ->
            new SbomCheck.ProbeResult(404));

        var candidates = check.candidates(component());

        assertEquals(
            "https://repo.maven.apache.org/maven2/org/example/demo/1.2.3/demo-1.2.3-cyclonedx.json",
            candidates.get(0));
        assertEquals(
            "https://repo.maven.apache.org/maven2/org/example/demo/1.2.3/demo-1.2.3-cyclonedx.xml",
            candidates.get(1));
        assertEquals(
            "https://repo.maven.apache.org/maven2/org/example/demo/1.2.3/demo-1.2.3.spdx.json",
            candidates.get(2));
    }

    private static Coordinate component() {
        return new Coordinate("org.example", "demo", "1.2.3", Coordinate.Kind.DEPENDENCY);
    }
}
