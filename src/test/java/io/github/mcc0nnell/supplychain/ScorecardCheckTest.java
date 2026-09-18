package io.github.mcc0nnell.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ScorecardCheckTest {
    private static final URI REPOSITORY =
        URI.create("https://repo.maven.apache.org/maven2");
    private static final byte[] POM = """
        <project>
          <modelVersion>4.0.0</modelVersion>
          <scm>
            <url>https://gitbox.apache.org/repos/asf?p=commons-lang.git</url>
          </scm>
        </project>
        """.getBytes(StandardCharsets.UTF_8);

    @Test
    void reportsLiveScoreAsPass() {
        var check = check(200, """
            {"date":"2026-09-15T16:58:50Z","score":8.1}
            """, -1);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.PASS, evidence.status());
        assertEquals("OpenSSF Scorecard 8.1 (2026-09-15T16:58:50Z)", evidence.summary());
        assertTrue(evidence.locations().contains(
            "https://api.securityscorecards.dev/projects/github.com/apache/commons-lang"));
    }

    @Test
    void appliesConfiguredMinimumScore() {
        var check = check(200, """
            {"date":"2026-09-15","score":6.9}
            """, 7.0);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.FAIL, evidence.status());
        assertEquals(
            "OpenSSF Scorecard 6.9 (2026-09-15) is below required minimum 7",
            evidence.summary());
    }

    @Test
    void missingScorecardIsFail() {
        var check = check(404, "{}", -1);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.FAIL, evidence.status());
        assertEquals("OpenSSF Scorecard result is not published", evidence.summary());
    }

    @Test
    void transientScorecardResponseRemainsUnknown() {
        var check = check(503, "{}", -1);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertEquals("OpenSSF Scorecard lookup returned HTTP 503", evidence.summary());
    }

    @Test
    void malformedSuccessResponseRemainsUnknown() {
        var check = check(200, "{\"date\":\"2026-09-15\"}", -1);

        var evidence = check.inspect(component());

        assertEquals(Evidence.Status.UNKNOWN, evidence.status());
        assertEquals(
            "OpenSSF Scorecard response did not contain a numeric score",
            evidence.summary());
    }

    private static ScorecardCheck check(
        int status,
        String response,
        double minimumScore) {

        var resolver = new ScmResolver(REPOSITORY, uri ->
            new ScmResolver.FetchResult(200, POM));
        return new ScorecardCheck(
            resolver,
            uri -> new ScorecardCheck.FetchResult(
                status,
                response.getBytes(StandardCharsets.UTF_8)),
            minimumScore);
    }

    private static Coordinate component() {
        return new Coordinate(
            "org.apache.commons",
            "commons-lang3",
            "3.17.0",
            Coordinate.Kind.DEPENDENCY);
    }
}
