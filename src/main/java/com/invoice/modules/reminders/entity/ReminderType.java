package com.invoice.modules.reminders.entity;

/**
 * The five reminder touch-points in an invoice's lifecycle.
 */
public enum ReminderType {
    PRE_DUE,   // 3 days before due date
    ON_DUE,    // on the due date
    POST_3,    // 3 days after due date
    POST_7,    // 7 days after due date
    POST_14    // 14 days after due date
}
