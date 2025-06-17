package com.kapil.cognos.exception;

public class CustomException extends RuntimeException {
	  
	private static final long serialVersionUID = 1L;
		private String message;

	    public CustomException() {}

	    public CustomException(String msg) {
	        super(msg);
	        this.message = msg;
	    }
}