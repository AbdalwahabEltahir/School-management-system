/**
 * <h2>Module issues</h2>
 * Issues, allowed exceptions and major incidents. <code>IssueService</code> is the status state machine; <code>IssueWorkflowService</code> is what owners and coordinators can do; <code>IssueFollowUpService</code> runs the reminder timers. Announces everything with <code>IssueEvents</code>; depends only on <code>shared</code>.
 */
package qa.sijill.sijill.issues;
