package io.github.mcc0nnell.supplychain;

import java.util.List;
final class ScorecardCheck implements EvidenceCheck {
    public String id() { return "openssf-scorecard"; }
    public Evidence inspect(Coordinate c) {
        return new Evidence(id(), Evidence.Status.UNKNOWN,
            "resolve Maven coordinate to canonical SCM before Scorecard lookup", List.of());
    }
}
