package qa.sijill.sijill.support.model;

/**
 * Who a ticket is written to: the school's own School IT; Leadership at head office (principals only, handled by EAA IT,
 * Leadership can read them); or EAA IT (Leadership only: Leadership is read-only and asks EAA IT for any change).
 */
public enum TicketAudience { SCHOOL_IT, LEADERSHIP, EAA_IT }
