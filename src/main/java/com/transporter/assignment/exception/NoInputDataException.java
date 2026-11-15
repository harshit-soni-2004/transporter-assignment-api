package com.transporter.assignment.exception;

public class NoInputDataException extends AppException {

    public NoInputDataException() {
        super("No input data has been submitted yet.", 409);
    }
}
