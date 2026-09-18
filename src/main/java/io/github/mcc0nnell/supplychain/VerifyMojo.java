package io.github.mcc0nnell.supplychain;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;

@Mojo(name="verify", defaultPhase=LifecyclePhase.VERIFY, threadSafe=true,
    requiresDependencyResolution=ResolutionScope.TEST)
public final class VerifyMojo extends AbstractMojo {
    @Parameter(defaultValue="${project}", readonly=true, required=true)
    MavenProject project;

    @Parameter(defaultValue="${project.build.directory}/supply-chain-verification.ndjson")
    File reportFile;

    @Component
    RepositorySystem repositorySystem;

    @Parameter(defaultValue="${repositorySystemSession}", readonly=true, required=true)
    RepositorySystemSession repositorySystemSession;

    @Parameter(property="supplyChainVerification.requestTimeoutSeconds", defaultValue="5")
    int requestTimeoutSeconds;

    @Parameter(property="supplyChainVerification.parallelism", defaultValue="8")
    int parallelism;

    @Parameter(property="supplyChainVerification.minimumScorecardScore", defaultValue="-1")
    double minimumScorecardScore;

    @Parameter(property="supplyChainVerification.failOnFailure", defaultValue="false")
    boolean failOnFailure;

    @Parameter(property="supplyChainVerification.failOnUnknown", defaultValue="false")
    boolean failOnUnknown;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (requestTimeoutSeconds <= 0) {
                throw new MojoFailureException(
                    "supplyChainVerification.requestTimeoutSeconds must be positive");
            }
            if (parallelism <= 0) {
                throw new MojoFailureException(
                    "supplyChainVerification.parallelism must be positive");
            }

            Duration timeout = Duration.ofSeconds(requestTimeoutSeconds);
            MavenEvidenceResolver mavenResolver = new MavenEvidenceResolver(
                repositorySystem,
                repositorySystemSession,
                project.getRemoteProjectRepositories(),
                project.getRemotePluginRepositories());
            ScmResolver scmResolver = new ScmResolver(mavenResolver);

            List<Coordinate> components = components().stream()
                .map(mavenResolver::enrich)
                .toList();
            List<EvidenceCheck> checks = List.of(
                new SbomCheck(mavenResolver),
                new ScorecardCheck(
                    scmResolver,
                    timeout,
                    minimumScorecardScore,
                    mavenResolver.offline()));

            List<String> lines = new ArrayList<>();
            int passed = 0;
            int warned = 0;
            int failed = 0;
            int unknown = 0;

            for (Observation observation : inspect(components, checks)) {
                Evidence evidence = observation.evidence();
                switch (evidence.status()) {
                    case PASS -> passed++;
                    case WARN -> warned++;
                    case FAIL -> failed++;
                    case UNKNOWN -> unknown++;
                }
                lines.add(toJson(observation.component(), evidence));
            }

            Files.createDirectories(reportFile.toPath().getParent());
            Files.write(reportFile.toPath(), lines, StandardCharsets.UTF_8);
            getLog().info("components=" + components.size()
                + " observations=" + lines.size()
                + " pass=" + passed
                + " warn=" + warned
                + " fail=" + failed
                + " unknown=" + unknown
                + " report=" + reportFile);

            enforcePolicy(failed, unknown);
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("supply-chain verification failed", e);
        }
    }

    private List<Observation> inspect(
        List<Coordinate> components,
        List<EvidenceCheck> checks) throws Exception {

        int taskCount = components.size() * checks.size();
        int workers = Math.min(parallelism, Math.max(1, taskCount));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<Observation>> futures = new ArrayList<>(taskCount);

        try {
            for (Coordinate component : components) {
                for (EvidenceCheck check : checks) {
                    futures.add(executor.submit(
                        () -> new Observation(component, check.inspect(component))));
                }
            }

            List<Observation> observations = new ArrayList<>(taskCount);
            for (Future<Observation> future : futures) {
                observations.add(future.get());
            }
            return observations;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Coordinate> components() {
        List<Coordinate> components = new ArrayList<>();
        for (Artifact artifact : project.getArtifacts()) {
            String extension = artifact.getArtifactHandler() == null
                ? artifact.getType()
                : artifact.getArtifactHandler().getExtension();
            components.add(new Coordinate(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                Coordinate.Kind.DEPENDENCY,
                extension,
                artifact.getClassifier()));
        }
        for (Plugin plugin : project.getBuildPlugins()) {
            components.add(new Coordinate(
                plugin.getGroupId(),
                plugin.getArtifactId(),
                plugin.getVersion(),
                Coordinate.Kind.BUILD_PLUGIN,
                "jar",
                ""));
        }
        components.sort(
            Comparator.comparing(Coordinate::gav)
                .thenComparing(component -> component.kind().name()));
        return components;
    }

    private void enforcePolicy(int failed, int unknown) throws MojoFailureException {
        List<String> violations = new ArrayList<>(2);
        if (failOnFailure && failed > 0) {
            violations.add("failed checks=" + failed);
        }
        if (failOnUnknown && unknown > 0) {
            violations.add("unknown checks=" + unknown);
        }
        if (!violations.isEmpty()) {
            throw new MojoFailureException(
                "supply-chain verification policy rejected build: "
                    + String.join(", ", violations));
        }
    }

    private record Observation(Coordinate component, Evidence evidence) {}

    private static String toJson(Coordinate component, Evidence evidence) {
        return "{\"schemaVersion\":1"
            + ",\"coordinates\":\"" + esc(component.displayCoordinates())
            + "\",\"gav\":\"" + esc(component.gav())
            + "\",\"kind\":\"" + component.kind()
            + "\",\"artifact\":" + resolvedArtifactJson(component.artifact())
            + ",\"pom\":" + resolvedArtifactJson(component.pom())
            + ",\"check\":\"" + esc(evidence.check())
            + "\",\"status\":\"" + evidence.status()
            + "\",\"summary\":\"" + esc(evidence.summary())
            + "\",\"locations\":" + jsonArray(evidence.locations()) + "}";
    }

    private static String resolvedArtifactJson(ResolvedArtifact artifact) {
        if (artifact == null) {
            return "null";
        }
        return "{\"state\":\"" + artifact.state()
            + "\",\"repositoryId\":\"" + esc(artifact.repositoryId())
            + "\",\"sha256\":\"" + esc(artifact.sha256())
            + "\",\"summary\":\"" + esc(artifact.summary()) + "\"}";
    }

    static String jsonArray(List<String> values) {
        var out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(esc(values.get(i))).append('"');
        }
        return out.append(']').toString();
    }

    static String esc(String value) {
        if (value == null) {
            return "";
        }

        var out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }
}
