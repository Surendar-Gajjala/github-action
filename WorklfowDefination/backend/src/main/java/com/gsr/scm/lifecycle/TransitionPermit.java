package com.gsr.scm.lifecycle;

import com.gsr.scm.item.LifecycleStatus;

/**
 * Capability token proving a lifecycle transition was validated by {@link LifecycleService}.
 * The constructor is package-private so no other code can change an Item's status.
 */
public final class TransitionPermit {

    private final LifecycleStatus target;

    TransitionPermit(LifecycleStatus target) {
        this.target = target;
    }

    public LifecycleStatus target() {
        return target;
    }
}
