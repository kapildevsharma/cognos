package com.kapil.cognos.service;

public class CognosRetryService {

	
	public Object fallback(Throwable t) {
	    System.out.println("Fallback triggered due to: " + t.getMessage());
	    return null;
	}
	
}
