package io.github.mcc0nnell.supplychain;

import java.net.URI;
import java.util.List;
final class SbomCheck implements EvidenceCheck {
    private final URI repository;
    SbomCheck(URI repository) { this.repository = repository; }
    public String id() { return "public-sbom"; }
    public Evidence inspect(Coordinate c) {
        String stem = repository.toString().replaceAll("/+$", "") + "/"
            + c.groupId().replace('.', '/') + "/" + c.artifactId() + "/" + c.version() + "/"
            + c.artifactId() + "-" + c.version();
        return new Evidence(id(), Evidence.Status.UNKNOWN, "public SBOM requires verification",
            List.of(stem + "-cyclonedx.json", stem + "-cyclonedx.xml", stem + ".spdx.json"));
    }
}
