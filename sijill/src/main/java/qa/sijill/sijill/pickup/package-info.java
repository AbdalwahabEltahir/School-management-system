/**
 * <h2>Module pickup</h2>
 * Student pick-up and collection: parents keep a list of approved collectors and press "I have arrived"; teachers and
 * coordinators verify the collector and release the child; every release goes into an append-only chain-of-custody log.
 * Tells people through {@code PersonNotice}, so it does not depend on the notifications module.
 */
package qa.sijill.sijill.pickup;
