package com.atlas.backend.dependency;

import jakarta.persistence.*;

@Entity
@Table(name = "commitment_dependency")
public class CommitmentDependency {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "blocking_commitment_id", nullable = false) private Long blockingCommitmentId;
    @Column(name = "blocked_commitment_id", nullable = false) private Long blockedCommitmentId;
    protected CommitmentDependency() { }
    static CommitmentDependency of(Long blocking, Long blocked) {
        CommitmentDependency edge = new CommitmentDependency();
        edge.blockingCommitmentId = blocking;
        edge.blockedCommitmentId = blocked;
        return edge;
    }
    public Long getId() { return id; }
    public Long getBlockingCommitmentId() { return blockingCommitmentId; }
    public Long getBlockedCommitmentId() { return blockedCommitmentId; }
}
