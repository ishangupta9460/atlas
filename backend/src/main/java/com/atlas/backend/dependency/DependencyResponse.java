package com.atlas.backend.dependency;

public record DependencyResponse(Long blockingCommitmentId, Long blockedCommitmentId) {
    static DependencyResponse from(CommitmentDependency edge) {
        return new DependencyResponse(edge.getBlockingCommitmentId(), edge.getBlockedCommitmentId());
    }
}
