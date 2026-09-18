package io.github.mcc0nnell.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ScmResolverTest {
    @Test
    void resolvesApacheGitboxToGithubMirror() {
        var resolver = resolver(pom(
            "https://gitbox.apache.org/repos/asf?p=commons-lang.git"));

        var result = resolver.resolve(component());

        assertTrue(result.resolved());
        assertEquals("github.com/apache/commons-lang", result.scorecardProject());
        assertTrue(result.locations().get(0).startsWith("maven-local:"));
    }

    @Test
    void resolvesDirectGithubScm() {
        var resolver = resolver(pom(
            "scm:git:https://github.com/example/demo.git"));

        var result = resolver.resolve(component());

        assertTrue(result.resolved());
        assertEquals("github.com/example/demo", result.scorecardProject());
    }

    @Test
    void resolvesScmInheritedFromParentPom() {
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

        var resolver = new ScmResolver(component ->
            component.artifactId().equals("surefire")
                ? new ScmResolver.PomResult(
                    true, parent, "maven-local:org.apache.maven.surefire:surefire:3.2.5:pom")
                : new ScmResolver.PomResult(
                    true, child, "maven-local:org.apache.maven.plugins:maven-surefire-plugin:3.2.5:pom"));

        var result = resolver.resolve(new ResolvedComponent(
            "org.apache.maven.plugins", "maven-surefire-plugin", "3.2.5", "3.2.5",
            "maven-plugin", null, "jar", ResolvedComponent.Kind.BUILD_PLUGIN,
            URI.create("https://repo.maven.apache.org/maven2"), "central", "abc"));

        assertTrue(result.resolved());
        assertEquals("github.com/apache/maven-surefire", result.scorecardProject());
        assertEquals(
            "source repository inherited from parent SCM metadata in Maven-resolved POM",
            result.summary());
    }

    @Test
    void ignoresNestedScmElementsThatAreNotProjectMetadata() throws Exception {
        byte[] nested = """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <build>
                <plugins>
                  <plugin>
                    <configuration>
                      <scm><url>https://github.com/apache/commons-lang</url></scm>
                    </configuration>
                  </plugin>
                </plugins>
              </build>
            </project>
            """.getBytes(StandardCharsets.UTF_8);

        assertTrue(ScmResolver.scmCandidates(nested).isEmpty());
    }

    @Test
    void rejectsScmWhenArtifactAndPomCameFromDifferentRepositories() {
        var component = new ResolvedComponent(
            "org.example", "shadow", "1.0", "1.0",
            "jar", null, "jar", ResolvedComponent.Kind.DEPENDENCY,
            URI.create("https://shadow.example.test/maven2"), "shadow",
            URI.create("https://repo.maven.apache.org/maven2"), "central",
            "artifact-sha", "pom-sha", null);
        var resolver = resolver(pom("https://github.com/apache/commons-lang"));

        var result = resolver.resolve(component);

        assertFalse(result.resolved());
        assertEquals(
            "artifact and POM were resolved from different repositories; SCM association is not trusted",
            result.summary());
        assertTrue(result.locations().contains("artifact-repository:shadow"));
        assertTrue(result.locations().contains("pom-repository:central"));
    }

    @Test
    void unsupportedScmRemainsUnknown() {
        var resolver = resolver(pom(
            "https://git.example.org/example/demo.git"));

        var result = resolver.resolve(component());

        assertFalse(result.resolved());
        assertEquals(
            "SCM metadata is not a supported public Git repository",
            result.summary());
    }

    @Test
    void missingLocalPomRemainsUnknown() {
        var resolver = new ScmResolver(component ->
            new ScmResolver.PomResult(false, new byte[0], "maven-local:" + component.gav()));

        var result = resolver.resolve(component());

        assertFalse(result.resolved());
        assertEquals(
            "Maven-resolved POM not found in the local repository",
            result.summary());
    }

    @Test
    void canonicalizesGithubSshForms() {
        assertEquals(
            "github.com/example/demo",
            ScmResolver.canonicalize("git@github.com:example/demo.git").orElseThrow());
        assertEquals(
            "github.com/example/demo",
            ScmResolver.canonicalize("scm:git:ssh://git@github.com/example/demo.git")
                .orElseThrow());
    }

    private static ScmResolver resolver(byte[] pom) {
        return new ScmResolver(component ->
            new ScmResolver.PomResult(true, pom, "maven-local:" + component.gav() + ":pom"));
    }

    private static ResolvedComponent component() {
        return new ResolvedComponent(
            "org.apache.commons", "commons-lang3", "3.17.0", "3.17.0",
            "jar", null, "jar", ResolvedComponent.Kind.DEPENDENCY,
            URI.create("https://repo.maven.apache.org/maven2"), "central", "abc");
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
