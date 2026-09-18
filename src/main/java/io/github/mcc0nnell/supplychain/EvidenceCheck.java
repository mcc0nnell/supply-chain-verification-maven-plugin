package io.github.mcc0nnell.supplychain;
interface EvidenceCheck {
    String id();
    Evidence inspect(Coordinate component);
}
