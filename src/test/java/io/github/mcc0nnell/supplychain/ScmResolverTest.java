package io.github.mcc0nnell.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ScmResolverTest {
    @Test
    void resolvesApacheGitboxToGithubMirror() {
        var resolver = new ScmResolver(component ->
            found(pom("https://gitbox.apache.org/repos/asf?p=commons-lang.git")));

        var result = resolver.resolve(component());

        assertTrue(result.resolved());
        assertEquals("github.com/apache/commons-lang", result.scorecardProject());
        assertTrue(result.locations().get(0).contains("fixture"));
    }

    @Test
    void resolvesDirectGithubScm() {
        var resolver = new ScmResolver(component ->
            found(pom("scm:git:https://github.com/example/demo.git")));

        var result = resolver.resolve(component());

        assertTrue(result.resolved());
        assertEquals("github.com/example/demo", result.scorecardProject());
    }

    @Test
    void resolvesScmInheritedFromParentPomUsingSameKind() {
        byte[] child = """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <groupId>org.apache.maven.surefire</groupId>
                <artifactId>surefire</artifactId>
                <version>3.2.5</version>
              </parent>
            </project>
            """.getBytes(StandardCharsets.UTF_8);
        byte[] parent = pom(
            "https://github.com/apache/maven-surefire/tree/${project.scm.tag}");

        var resolver = new ScmResolver(component -> {
            if ("surefire".equals(component.artifactId())) {
                assertEquals(Coordinate.Kind.BUILD_PLUGIN, component.kind());
                return found(parent);
            }
            return found(child);
        });

        var result = resolver.resolve(new Coordinate(
            "org.apache.maven.plugins",
            "maven-surefire-plugin",
            "3.2.5",
            Coordinate.Kind.BUILD_PLUGIN));

        assertTrue(result.resolved());
        assertEquals("github.com/apache/maven-surefire", result.scorecardProject());
        assertEquals(
            "canonical source repository inherited from Maven-resolved parent POM",
            result.summary());
    }

    @Test
    void unsupportedScmRemainsUnknown() {
        var resolver = new ScmResolver(component ->
            found(pom("https://git.example.org/example/demo.git")));

        var result = resolver.resolve(component());

        assertFalse(result.resolved());
        assertEquals(
            "SCM metadata is not a supported public Git repository",
            result.summary());
    }

    @Test
    void missingPomRemainsUnknown() {
        var resolver = new ScmResolver(component ->
            new ScmResolver.FetchResult(
                ResolvedArtifact.State.MISSING,
                new byte[0],
                "fixture:pom",
                "not found"));

        var result = resolver.resolve(component());

        assertFalse(result.resolved());
        assertEquals(
            "published POM not found in Maven resolution context",
            result.summary());
    }

    @Test
    void canonicalizesGithubSshForms() {
        assertEquals(
            "github.com/example/demo",
            ScmResolver.canonicalize("git@github.com:example/demo.git").orElseThrow());
        assertEquals(
            "github.com/example/demo",
            ScmResolver.canonicalize(
                "scm:git:ssh://git@github.com/example/demo.git").orElseThrow());
    }

    private static ScmResolver.FetchResult found(byte[] body) {
        return new ScmResolver.FetchResult(
            ResolvedArtifact.State.FOUND,
            body,
            "maven:fixture:sha256:abc",
            "resolved");
    }

    private static Coordinate component() {
        return new Coordinate(
            "org.apache.commons",
            "commons-lang3",
            "3.17.0",
            Coordinate.Kind.DEPENDENCY);
    }

    private static byte[] pom(String scmUrl) {
        return ("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <scm>
                <url>%s</url>
              </scm>
            </project>
            """.formatted(scmUrl)).getBytes(StandardCharsets.UTF_8);
    }
}
