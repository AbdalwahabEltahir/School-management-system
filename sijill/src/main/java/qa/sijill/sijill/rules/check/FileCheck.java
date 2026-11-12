package qa.sijill.sijill.rules.check;

import qa.sijill.sijill.rules.model.CheckType;

/**
 * One kind of check a rule can use (proposal 5.3 "the nine check types").
 *
 * <p><b>To add a new kind of check:</b> add a value to {@link CheckType}, write one class that
 * implements this interface and annotate it {@code @Component}. {@link CheckRegistry} picks it up and
 * refuses to start if a {@code CheckType} has no implementation.
 *
 * <p>Implementations must be <b>pure with respect to the school's files</b>: they only look at the data
 * in the {@link CheckContext} and never write to the workspace (design principle 1, read-only always).
 * They decide only whether the structural condition is broken; whether the rule <i>applies</i> to the
 * file (document type / sensitivity) is decided by the rule engine, not here.
 */
public interface FileCheck {

    /** The check type this class implements. */
    CheckType type();

    /** Evaluates the file in the context and reports whether the condition is broken, and why. */
    CheckOutcome evaluate(CheckContext context);
}
