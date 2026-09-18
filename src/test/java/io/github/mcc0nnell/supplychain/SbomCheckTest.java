package io.github.mcc0nnell.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SbomCheckTest {
    private static final URI REPOSITORY =
        URI.create("https://repo.maven.apache.org/maven2");

    @Test
    void passesOnlyForSidecarExistenceAndSaysContentIsUnvalidated() {
        var check = new SbomCheck((component, sidecar) ->
            sidecar.location().endsWith("-cyclonedx.json")
                ? SbomCheck.ProbeResult.foundResult()
                : SbomCheck.ProbeResult.missingResult());

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.PASS, evidence.status());
        assertEquals(
            "public SBOM sidecar resolved through Maven; content and artifact binding are not yet validated",
            evidence.summary());
        assertEquals("false", evidence.attributes().get("contentValidated"));
        assertEquals("maven-resolver", evidence.attributes().get("resolution"));
    }

    @Test
    void usesRepositoryBoundToResolvedComponent() {
        var seen = new AtomicInteger();
        var component = component(URI.create("https://mirror.example.test/maven2"));
        var check = new SbomCheck((resolved, sidecar) -> {
            if (!sidecar.location().contains("mirror.example.test")) {
                throw new AssertionError("wrong repository: " + sidecar.location());
            }
            seen.incrementAndGet();
            return SbomCheck.ProbeResult.missingResult();
        });

        var evidence = check.inspect(component);

        assertEquals(Evidence.Status.FAIL, evidence.status());
        assertEquals(3, seen.get());
    }

    @Test
    void unresolvedProbeResultRemainsUnknown() {
        var check = new SbomCheck((component, sidecar) ->
            SbomCheck.ProbeResult.unknownResult());

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
    }

    @Test
    void classifiedArtifactsRemainUnknown() {
        var component = new ResolvedComponent(
            "org.example", "demo", "1.2.3", "1.2.3",
            "test-jar", "tests", "jar", ResolvedComponent.Kind.DEPENDENCY,
            REPOSITORY, "central", "abc");
        var check = new SbomCheck((resolved, sidecar) ->
            SbomCheck.ProbeResult.foundResult());

        var evidence = check.inspect(component);

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertEquals(
            "SBOM sidecar convention is not defined for classified artifacts",
            evidence.summary());
    }

    @Test
    void missingResolvedRepositoryRemainsUnknown() {
        var check = new SbomCheck((resolved, sidecar) ->
            SbomCheck.ProbeResult.foundResult());

        var evidence = check.inspect(new ResolvedComponent(
            "org.example", "demo", "1.2.3", ResolvedComponent.Kind.DEPENDENCY));

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
    }

    @Test
    void emitsKnownCandidateLocationsDeterministically() {
        var check = new SbomCheck((resolved, sidecar) ->
            SbomCheck.ProbeResult.missingResult());

        var candidates = check.sidecars(component());

        assertEquals(
            "https://repo.maven.apache.org/maven2/org/example/demo/1.2.3/demo-1.2.3-cyclonedx.json",
            candidates.get(0).location());
        assertEquals(
            "https://repo.maven.apache.org/maven2/org/example/demo/1.2.3/demo-1.2.3-cyclonedx.xml",
            candidates.get(1).location());
        assertEquals(
            "https://repo.maven.apache.org/maven2/org/example/demo/1.2.3/demo-1.2.3.spdx.json",
            candidates.get(2).location());
    }

    private static ResolvedComponent component() {
        return component(REPOSITORY);
    }

    private static ResolvedComponent component(URI repository) {
        return new ResolvedComponent(
            "org.example", "demo", "1.2.3", "1.2.3",
            "jar", null, "jar", ResolvedComponent.Kind.DEPENDENCY,
            repository, "central", "abc");
    }
}
