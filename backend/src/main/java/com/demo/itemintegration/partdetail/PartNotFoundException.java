package com.demo.itemintegration.partdetail;

/** No part exists with the requested id (or the object is not a part). */
public class PartNotFoundException extends RuntimeException {

    public PartNotFoundException(long partId) {
        super("No part found with id " + partId);
    }
}
