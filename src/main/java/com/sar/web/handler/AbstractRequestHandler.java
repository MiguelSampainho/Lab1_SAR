package com.sar.web.handler;

import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;

public abstract class AbstractRequestHandler {
    protected static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    
    // Template method pattern
    public void handle(Request request, Response response) {
        // Pre-processing
        preHandle(request, response);
        
        // Handle request based on HTTP method
        switch (request.method.toUpperCase()) {
            case "GET":
                handleGet(request, response);
                break;
            case "POST":
                handlePost(request, response);
                break;
            default:
                handleUnsupportedMethod(request, response);
        }
        
        // Post-processing
        postHandle(request, response);
    }

    protected void preHandle(Request request, Response response) {
        // Default pre-processing, can be overridden
    }

    protected void postHandle(Request request, Response response) {
        // Default post-processing, can be overridden
    }

    protected abstract void handleGet(Request request, Response response);
    protected abstract void handlePost(Request request, Response response);
    
    protected void handleUnsupportedMethod(Request request, Response response) {
        response.setError(ReplyCode.NOTIMPLEMENTED, request.version);
    }
}