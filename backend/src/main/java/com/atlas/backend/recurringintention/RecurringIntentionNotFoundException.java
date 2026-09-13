package com.atlas.backend.recurringintention;

public class RecurringIntentionNotFoundException extends RuntimeException {
    public RecurringIntentionNotFoundException() { super("Recurring intention not found"); }
}
