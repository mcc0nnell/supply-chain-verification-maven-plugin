package io.github.mcc0nnell.supplychain;

record Coordinate(
    String groupId,
    String artifactId,
    String version,
    Kind kind,
    String extension,
    String classifier,
    ResolvedArtifact artifact,
    ResolvedArtifact pom) {

    enum Kind { DEPENDENCY, BUILD_PLUGIN }

    Coordinate(String groupId, String artifactId, String version, Kind kind) {
        this(groupId, artifactId, version, kind, "jar", "", null, null);
    }

    Coordinate(
        String groupId,
        String artifactId,
        String version,
        Kind kind,
        String extension,
        String classifier) {
        this(groupId, artifactId, version, kind, extension, classifier, null, null);
    }

    String gav() {
        return groupId + ":" + artifactId + ":"
            + (version == null ? "UNKNOWN" : version);
    }

    String displayCoordinates() {
        String ext = extension == null || extension.isBlank() ? "jar" : extension;
        String cls = classifier == null || classifier.isBlank() ? "" : ":" + classifier;
        return groupId + ":" + artifactId + ":" + ext + cls + ":" + version;
    }

    Coordinate withResolution(ResolvedArtifact artifact, ResolvedArtifact pom) {
        return new Coordinate(
            groupId, artifactId, version, kind,
            extension, classifier, artifact, pom);
    }
}
