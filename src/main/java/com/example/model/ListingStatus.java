package com.example.model;

public enum ListingStatus {
    DRAFT,
    PENDING_REVIEW,
    REJECTED,
    ACTIVE,
    SOLD,
    ARCHIVED;
    // switch без default: когда появится новый статус, компилятор сам покажет это место.
    // С модерацией публикация идёт через PENDING_REVIEW, без неё — сразу в ACTIVE. Кто какой переход делает,
    // решают Listing и ListingAccessPolicy: здесь только какие переходы вообще бывают
    public boolean canTransitionTo(ListingStatus target)
    {
        return switch (this)
        {
            case DRAFT, ARCHIVED, REJECTED -> target == ACTIVE || target == PENDING_REVIEW;
            case PENDING_REVIEW -> target == ACTIVE || target == REJECTED;
            case ACTIVE -> target == SOLD || target == ARCHIVED;
            case SOLD -> false;
        };
    }
}
