package com.demo.itemintegration.itemdetail;

/** No item exists in the hosted data for the requested id (or the id belongs to another entity). */
public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(long itemId) {
        super("Item " + itemId + " not found");
    }
}
