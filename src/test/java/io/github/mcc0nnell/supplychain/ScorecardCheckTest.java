package io.github.mcc0nnell.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ScorecardCheckTest {
    private static final byte[] POM = """
        <project>
          <modelVersion>4.0.0</modelVersion>
          <scm>
            <url>https://gitbox.apache.org/repos/asf?p=commons-lang.git</url>
          </scm>
        </project>
        """.getBytes(StandardCharsets.UTF_8);

    @Test
    void reportsCurrentRepositoryScoreAndCommitAsPass() {
        var check = check(200, """
            {
              "date":"2026-09-15T16:58:50Z",
              "score":8.1,
              "repo":{
                "name":"github.com/apache/commons-lang",
                "commit":"01a66dd238cb3c6a143da80ca183845a7da747aa"
              }
            }
            """, -1);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.PASS, evidence.status());
        assertTrue(evidence.summary().contains("current OpenSSF Scorecard 8.1"));
        assertTrue(evidence.summary().contains("not artifact-version provenance"));
        assertEquals("false", evidence.attributes().get("artifactVersionBound"));
        assertEquals(
            "01a66dd238cb3c6a143da80ca183845a7da747aa",
            evidence.attributes().get("commit"));
    }

    @Test
    void appliesConfiguredMinimumScore() {
        var check = check(200, """
            {
              "date":"2026-09-15",
              "score":6.9,
              "repo":{"name":"github.com/apache/commons-lang","commit":"abcdef"}
            }
            """, 7.0);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.FAIL, evidence.status());
        assertTrue(evidence.summary().contains("below required minimum 7"));
    }

    @Test
    void missingScorecardIsFail() {
        var check = check(404, "{}", -1);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.FAIL, evidence.status());
        assertTrue(evidence.summary().contains("current OpenSSF Scorecard result is not published"));
    }

    @Test
    void transientScorecardResponseRemainsUnknown() {
        var check = check(503, "{}", -1);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertEquals("OpenSSF Scorecard lookup returned HTTP 503", evidence.summary());
    }

    @Test
    void nestedCheckScoreCannotMasqueradeAsTopLevelScore() {
        var check = check(200, """
            {
              "repo":{"name":"github.com/apache/commons-lang","commit":"abcdef"},
              "checks":[{"name":"Dangerous-Workflow","score":10}]
            }
            """, -1);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertEquals(
            "OpenSSF Scorecard response did not contain a top-level numeric score",
            evidence.summary());
    }

    @Test
    void mismatchedScorecardRepositoryRemainsUnknown() {
        var check = check(200, """
            {
              "score":9.9,
              "repo":{"name":"github.com/google/oss-fuzz","commit":"abcdef"}
            }
            """, -1);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertTrue(evidence.summary().contains("did not match the requested project"));
    }

    @Test
    void offlineModeDoesNotCallRemoteScorecardApi() {
        var resolver = resolver();
        var check = new ScorecardCheck(
            resolver,
            uri -> {
                throw new AssertionError("network fetch should not occur in offline mode");
            },
            -1,
            true);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertTrue(evidence.summary().contains("offline"));
    }

    private static ScorecardCheck check(
        int status,
        String response,
        double minimumScore) {

        return new ScorecardCheck(
            resolver(),
            uri -> new ScorecardCheck.FetchResult(
                status,
                response.getBytes(StandardCharsets.UTF_8)),
            minimumScore,
            false);
    }

    private static ScmResolver resolver() {
        return new ScmResolver(component ->
            new ScmResolver.PomResult(
                true,
                POM,
                "maven-local:org.apache.commons:commons-lang3:3.17.0:pom"));
    }

    private static ResolvedComponent component() {
        return new ResolvedComponent(
            "org.apache.commons", "commons-lang3", "3.17.0", "3.17.0",
            "jar", null, "jar", ResolvedComponent.Kind.DEPENDENCY,
            URI.create("https://repo.maven.apache.org/maven2"), "central", "abc");
    }
}
