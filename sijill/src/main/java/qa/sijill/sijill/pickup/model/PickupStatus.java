package qa.sijill.sijill.pickup.model;

/** Where an arrival stands: waiting for staff, ID being checked, or finished. */
public enum PickupStatus {
    ARRIVED, VERIFYING, RELEASED, DECLINED, CANCELLED;

    public boolean open() {
        return this == ARRIVED || this == VERIFYING;
    }
}
