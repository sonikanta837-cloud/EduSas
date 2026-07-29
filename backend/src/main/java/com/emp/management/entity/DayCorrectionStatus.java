package com.emp.management.entity;

// Describes a JobDailySummary day, not any single request — a day can accumulate
// multiple correction requests over time (e.g. REJECTED then resubmitted), so this
// always reflects the outcome of the latest one.
public enum DayCorrectionStatus {
    NONE,
    PENDING,
    APPROVED,
    REJECTED
}
