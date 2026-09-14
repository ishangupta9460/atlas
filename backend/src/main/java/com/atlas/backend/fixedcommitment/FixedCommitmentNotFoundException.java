package com.atlas.backend.fixedcommitment;

public class FixedCommitmentNotFoundException extends RuntimeException {
    public FixedCommitmentNotFoundException() { super("Fixed Commitment not found"); }
}
