package com.microservice.people.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) { 
        super(message); 
    }
}
