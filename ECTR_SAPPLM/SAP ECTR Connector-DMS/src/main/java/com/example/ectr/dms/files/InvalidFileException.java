// VERIFIED: plain Java.
package com.example.ectr.dms.files;

/** The uploaded file is empty, of a type we do not accept, or its name does not match its content. */
public class InvalidFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidFileException(String message) {
        super(message);
    }
}
