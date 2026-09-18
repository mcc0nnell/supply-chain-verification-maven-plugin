package io.github.mcc0nnell.supplychain;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name="verify", defaultPhase=LifecyclePhase.VERIFY, threadSafe=true,
    requiresDependencyResolution=ResolutionScope.TEST)
public final class VerifyMojo extends AbstractMojo {
    @Parameter(defaultValue="${project}", readonly=true, required=true)
    MavenProject project;
    @Parameter(defaultValue="${project.build.directory}/supply-chain-verification.ndjson")
    File reportFile;
    @Parameter(property="supplyChainVerification.failOnUnknown", defaultValue="false")
    boolean failOnUnknown;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            List<Coordinate> components = new ArrayList<>();
            for (Artifact a : project.getArtifacts()) {
                components.add(new Coordinate(a.getGroupId(), a.getArtifactId(), a.getVersion(),
                    Coordinate.Kind.DEPENDENCY));
            }
            for (Plugin p : project.getBuildPlugins()) {
                components.add(new Coordinate(p.getGroupId(), p.getArtifactId(), p.getVersion(),
                    Coordinate.Kind.BUILD_PLUGIN));
            }
            components.sort(Comparator.comparing(Coordinate::gav).thenComparing(c -> c.kind().name()));
            List<EvidenceCheck> checks = List.of(
                new SbomCheck(URI.create("https://repo.maven.apache.org/maven2")),
                new ScorecardCheck());
            List<String> lines = new ArrayList<>();
            int unknown = 0;
            for (Coordinate c : components) {
                for (EvidenceCheck check : checks) {
                    Evidence e = check.inspect(c);
                    if (e.status() == Evidence.Status.UNKNOWN) unknown++;
                    lines.add("{\"gav\":\"" + esc(c.gav())
                        + "\",\"kind\":\"" + c.kind()
                        + "\",\"check\":\"" + esc(e.check())
                        + "\",\"status\":\"" + e.status()
                        + "\",\"summary\":\"" + esc(e.summary())
                        + "\",\"locations\":" + jsonArray(e.locations()) + "}");
                }
            }
            Files.createDirectories(reportFile.toPath().getParent());
            Files.write(reportFile.toPath(), lines, StandardCharsets.UTF_8);
            getLog().info("components=" + components.size() + " observations=" + lines.size()
                + " report=" + reportFile);
            if (failOnUnknown && unknown > 0) {
                throw new MojoFailureException("unresolved supply-chain evidence: " + unknown);
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("supply-chain verification failed", e);
        }
    }

    static String jsonArray(List<String> values) {
        var out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append('"').append(esc(values.get(i))).append('"');
        }
        return out.append(']').toString();
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
