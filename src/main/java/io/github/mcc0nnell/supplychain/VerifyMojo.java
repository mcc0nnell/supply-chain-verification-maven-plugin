package io.github.mcc0nnell.supplychain;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
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
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Verifies supply-chain evidence for the artifacts Maven resolved for this build.
 *
 * <p>The goal writes deterministic NDJSON observations and can optionally reject the build when
 * conclusive failures or unresolved evidence are present.</p>
 */
@Mojo(
    name = "verify",
    defaultPhase = LifecyclePhase.VERIFY,
    threadSafe = true,
    requiresDependencyResolution = ResolutionScope.TEST)
public final class VerifyMojo extends AbstractMojo {
    private static final JsonFactory JSON = new JsonFactory();

    /** The Maven project being inspected. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    /** The current Maven session, including local repository and offline state. */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    MavenSession session;

    /** Maven Resolver entry point used for repository-native evidence resolution. */
    @Component
    RepositorySystem repositorySystem;

    /** Skip all supply-chain verification for this execution. */
    @Parameter(property = "supplyChainVerification.skip", defaultValue = "false")
    boolean skip;

    /** Destination for the deterministic NDJSON evidence report. */
    @Parameter(
        property = "supplyChainVerification.reportFile",
        defaultValue = "${project.build.directory}/supply-chain-verification.ndjson")
    File reportFile;

    /** Connect/request timeout, in seconds, for remote evidence lookups. */
    @Parameter(property = "supplyChainVerification.requestTimeoutSeconds", defaultValue = "5")
    int requestTimeoutSeconds;

    /** Maximum number of concurrent component/check observations. */
    @Parameter(property = "supplyChainVerification.parallelism", defaultValue = "8")
    int parallelism;

    /**
     * Optional minimum OpenSSF Scorecard score.
     *
     * <p>A negative value disables score-threshold enforcement while still requiring a published
     * Scorecard result for a {@code PASS}.</p>
     */
    @Parameter(property = "supplyChainVerification.minimumScorecardScore", defaultValue = "-1")
    double minimumScorecardScore;

    /** Reject the Maven build when one or more checks conclusively return {@code FAIL}. */
    @Parameter(property = "supplyChainVerification.failOnFailure", defaultValue = "false")
    boolean failOnFailure;

    /** Reject the Maven build when one or more checks return {@code UNKNOWN}. */
    @Parameter(property = "supplyChainVerification.failOnUnknown", defaultValue = "false")
    boolean failOnUnknown;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Supply-chain verification is skipped.");
            return;
        }

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
            Path localRepository = session.getRepositorySession()
                .getLocalRepository().getBasedir().toPath();
            ScmResolver resolver = new ScmResolver(localRepository);

            List<ResolvedComponent> components = components();
            List<EvidenceCheck> checks = List.of(
                new SbomCheck(repositorySystem, session.getRepositorySession()),
                new ScorecardCheck(
                    resolver,
                    timeout,
                    minimumScorecardScore,
                    session.isOffline()));

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

            Path reportPath = reportFile.toPath();
            Path parent = reportPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(reportPath, lines, StandardCharsets.UTF_8);

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
        List<ResolvedComponent> components,
        List<EvidenceCheck> checks) throws Exception {

        int taskCount = components.size() * checks.size();
        int workers = Math.min(parallelism, Math.max(1, taskCount));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<Observation>> futures = new ArrayList<>(taskCount);

        try {
            for (ResolvedComponent component : components) {
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

    private List<ResolvedComponent> components() throws IOException {
        List<ResolvedComponent> components = new ArrayList<>();

        for (Artifact artifact : project.getArtifacts()) {
            components.add(component(
                artifact,
                ResolvedComponent.Kind.DEPENDENCY,
                project.getRemoteProjectRepositories()));
        }
        for (Artifact artifact : project.getPluginArtifacts()) {
            components.add(component(
                artifact,
                ResolvedComponent.Kind.BUILD_PLUGIN,
                project.getRemotePluginRepositories()));
        }

        components.sort(
            Comparator.comparing(ResolvedComponent::gav)
                .thenComparing(component -> component.kind().name())
                .thenComparing(component -> nullToEmpty(component.type()))
                .thenComparing(component -> nullToEmpty(component.classifier())));
        return components;
    }

    private ResolvedComponent component(
        Artifact artifact,
        ResolvedComponent.Kind kind,
        List<RemoteRepository> repositories) throws IOException {

        String extension = artifact.getArtifactHandler() == null
            ? artifact.getType()
            : artifact.getArtifactHandler().getExtension();
        String classifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();
        String baseVersion = artifact.getBaseVersion() == null
            ? artifact.getVersion()
            : artifact.getBaseVersion();

        var artifactKey = new DefaultArtifact(
            artifact.getGroupId(),
            artifact.getArtifactId(),
            classifier,
            extension,
            baseVersion);
        var pomKey = new DefaultArtifact(
            artifact.getGroupId(),
            artifact.getArtifactId(),
            "",
            "pom",
            baseVersion);

        Source artifactSource = sourceOf(artifactKey, repositories);
        Source pomSource = sourceOf(pomKey, repositories);

        URI artifactRepositoryUri = artifactSource.repositoryUri();
        String artifactRepositoryId = artifactSource.repositoryId();

        File file = artifact.getFile();
        if (file == null || !file.isFile()) {
            file = artifactSource.file();
        }
        String sha256 = file != null && file.isFile()
            ? sha256(file.toPath())
            : null;
        String pomSha256 = pomSource.file() != null && pomSource.file().isFile()
            ? sha256(pomSource.file().toPath())
            : null;

        return new ResolvedComponent(
            artifact.getGroupId(),
            artifact.getArtifactId(),
            artifact.getVersion(),
            baseVersion,
            artifact.getType(),
            artifact.getClassifier(),
            extension,
            kind,
            artifactRepositoryUri,
            artifactRepositoryId,
            pomSource.repositoryUri(),
            pomSource.repositoryId(),
            sha256,
            pomSha256,
            artifactSource.repository());
    }

    private Source sourceOf(
        org.eclipse.aether.artifact.Artifact artifact,
        List<RemoteRepository> repositories) {

        var repositorySession = session.getRepositorySession();
        LocalArtifactResult result = repositorySession.getLocalRepositoryManager().find(
            repositorySession,
            new LocalArtifactRequest(artifact, repositories, null));

        RemoteRepository repository = result.getRepository();
        if (repository != null) {
            for (RemoteRepository configured : repositories) {
                if (repository.getId().equals(configured.getId())) {
                    repository = configured;
                    break;
                }
            }
        }

        URI repositoryUri = null;
        String repositoryId = null;
        if (repository != null) {
            repositoryId = repository.getId();
            try {
                URI raw = URI.create(repository.getUrl());
                repositoryUri = raw.getUserInfo() == null
                    ? raw
                    : new URI(
                        raw.getScheme(),
                        null,
                        raw.getHost(),
                        raw.getPort(),
                        raw.getPath(),
                        raw.getQuery(),
                        raw.getFragment());
            } catch (Exception e) {
                getLog().warn(
                    "Ignoring invalid Maven Resolver repository URL for "
                        + artifact + ": " + repository.getUrl());
            }
        }
        return new Source(repositoryUri, repositoryId, result.getFile(), repository);
    }

    private record Source(
        URI repositoryUri,
        String repositoryId,
        File file,
        RemoteRepository repository) {}

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

    private record Observation(ResolvedComponent component, Evidence evidence) {}

    static String toJson(ResolvedComponent component, Evidence evidence) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        try (JsonGenerator json = JSON.createGenerator(out)) {
            json.writeStartObject();
            json.writeNumberField("schemaVersion", 1);

            json.writeObjectFieldStart("component");
            json.writeStringField("gav", component.gav());
            json.writeStringField("groupId", component.groupId());
            json.writeStringField("artifactId", component.artifactId());
            nullable(json, "version", component.version());
            nullable(json, "baseVersion", component.baseVersion());
            nullable(json, "type", component.type());
            nullable(json, "classifier", component.classifier());
            nullable(json, "extension", component.extension());
            json.writeStringField("kind", component.kind().name());
            nullable(json, "artifactRepositoryId", component.artifactRepositoryId());
            nullable(
                json,
                "artifactRepositoryUrl",
                component.artifactRepositoryUri() == null
                    ? null
                    : component.artifactRepositoryUri().toString());
            nullable(json, "pomRepositoryId", component.pomRepositoryId());
            nullable(
                json,
                "pomRepositoryUrl",
                component.pomRepositoryUri() == null
                    ? null
                    : component.pomRepositoryUri().toString());
            nullable(json, "sha256", component.sha256());
            nullable(json, "pomSha256", component.pomSha256());
            json.writeEndObject();

            json.writeStringField("check", evidence.check());
            json.writeStringField("status", evidence.status().name());
            json.writeStringField("summary", evidence.summary());

            json.writeObjectFieldStart("attributes");
            for (Map.Entry<String, String> entry :
                new TreeMap<>(evidence.attributes()).entrySet()) {
                nullable(json, entry.getKey(), entry.getValue());
            }
            json.writeEndObject();

            json.writeArrayFieldStart("locations");
            for (String location : evidence.locations()) {
                json.writeString(location);
            }
            json.writeEndArray();
            json.writeEndObject();
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void nullable(JsonGenerator json, String name, String value)
        throws IOException {
        if (value == null) {
            json.writeNullField(name);
        } else {
            json.writeStringField(name, value);
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
