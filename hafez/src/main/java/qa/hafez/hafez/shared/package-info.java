/**
 * <h2>Module shared</h2>
 * Cross-cutting building blocks used by every module: base entities and shared enums (<code>model</code>), the access policy and current-user plumbing (<code>security</code>), audit and notice events (<code>events</code>), exceptions and the global error handler, the correlation-ID filter (<code>web</code>), school-calendar utilities and message wording (<code>util</code>), and the check/backup run record (<code>JobRun</code>).
 * <p><b>Rule:</b> shared depends on no business module (enforced by <code>ModuleStructureTest</code>).
 */
package qa.hafez.hafez.shared;
