package com.example.model;

public enum ListingStatus {
    DRAFT,
    ACTIVE,
    SOLD,
    ARCHIVED;
    // switch без default: когда появится новый статус, компилятор сам покажет это место
    public boolean canTransitionTo(ListingStatus target)
    {
        return switch (this)
        {
            case DRAFT, ARCHIVED -> target == ACTIVE;
            case ACTIVE -> target == SOLD || target == ARCHIVED;
            case SOLD -> false;
        };
    }
}
