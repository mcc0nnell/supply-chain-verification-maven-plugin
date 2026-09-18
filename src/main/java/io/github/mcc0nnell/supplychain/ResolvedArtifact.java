package io.github.mcc0nnell.supplychain;

import java.nio.file.Path;

record ResolvedArtifact(
    State state,
    Path file,
    String repositoryId,
    String sha256,
    String summary) {

    enum State { FOUND, MISSING, UNKNOWN }

    static ResolvedArtifact found(
        Path file,
        String repositoryId,
        String sha256) {
        return new ResolvedArtifact(
            State.FOUND, file, repositoryId, sha256, "resolved by Maven");
    }

    static ResolvedArtifact missing(String summary) {
        return new ResolvedArtifact(
            State.MISSING, null, null, null, summary);
    }

    static ResolvedArtifact unknown(String summary) {
        return new ResolvedArtifact(
            State.UNKNOWN, null, null, null, summary);
    }

    boolean found() {
        return state == State.FOUND;
    }

    String location() {
        if (!found()) {
            return summary;
        }
        String repo = repositoryId == null ? "unknown-repository" : repositoryId;
        String digest = sha256 == null ? "sha256:unknown" : "sha256:" + sha256;
        return "maven:" + repo + ":" + digest;
    }
}
