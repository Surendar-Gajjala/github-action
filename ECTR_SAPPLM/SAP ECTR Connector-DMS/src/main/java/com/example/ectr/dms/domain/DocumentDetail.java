// VERIFIED: plain Java.
package com.example.ectr.dms.domain;

/**
 * Metadata of an existing document as read back from SAP.
 *
 * @param key         full document key
 * @param description short text
 * @param status      internal status as returned by SAP (may be blank)
 */
public record DocumentDetail(DocumentKey key, String description, String status) {
}
