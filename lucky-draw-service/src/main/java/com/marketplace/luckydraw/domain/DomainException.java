package com.marketplace.luckydraw.domain;

public class DomainException extends RuntimeException {
    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static DomainException notFound() { return new DomainException("NOT_FOUND", "Campaign not found"); }
    public static DomainException forbidden() { return new DomainException("FORBIDDEN", "You cannot manage this campaign"); }
    public static DomainException campaignClosed() { return new DomainException("CAMPAIGN_CLOSED", "Campaign is closed"); }
    public static DomainException ticketUnusable() { return new DomainException("TICKET_UNUSABLE", "Ticket is invalid or already consumed"); }
    public static DomainException quotaReached() { return new DomainException("ENTRY_QUOTA_REACHED", "Entry quota reached"); }
    public static DomainException invalidTransition() { return new DomainException("INVALID_CAMPAIGN_STATE", "Campaign state transition is not allowed"); }
    public static DomainException notDrawable() { return new DomainException("NOT_DRAWABLE", "Campaign must be ended before drawing"); }
    public static DomainException noEntries() { return new DomainException("NO_ENTRIES", "Campaign has no entries to draw"); }
}
