package io.github.mcc0nnell.supplychain;

record Coordinate(String groupId, String artifactId, String version, Kind kind) {
    enum Kind { DEPENDENCY, BUILD_PLUGIN }
    String gav() { return groupId + ":" + artifactId + ":" + (version == null ? "UNKNOWN" : version); }
}
