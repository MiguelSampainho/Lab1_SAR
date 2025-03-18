package com.sar.controller;

import com.sar.web.handler.AbstractRequestHandler;
import com.sar.web.handler.ApiHandler;
import com.sar.web.handler.StaticFileHandler;
import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class HttpController {
    private static final Logger logger = LoggerFactory.getLogger(HttpController.class);
    
    private final Map<String, AbstractRequestHandler> handlers;
    private final StaticFileHandler defaultHandler;

    public HttpController(ApiHandler apiHandler, StaticFileHandler staticFileHandler) {
        this.handlers = new HashMap<>();
        this.defaultHandler = staticFileHandler;
        
        // Register handlers for different endpoints
        // You can register other handlers here for example for re-direction or simple authentication
        registerHandler("api", apiHandler);
    }

    /**
     * Registers a handler for a specific endpoint
     */
    public void registerHandler(String endpoint, AbstractRequestHandler handler) {
        handlers.put(endpoint.toLowerCase(), handler);
        logger.info("Registered handler for endpoint: {}", endpoint);
    }

    /**
     * Routes and handles the request
     */
    public void handleRequest(Request request, Response response) {
        try {
            //Check if request is null
            if (request == null) {
                response.setError(ReplyCode.BADREQ,"HTTP/1.1");
                return;
            }
            // Normalize URL
            String url = request.urlText.toLowerCase();
            while (url.startsWith("/")) {
                url = url.substring(1);
            }

            // Find appropriate handler
            AbstractRequestHandler handler = null;
            for (Map.Entry<String, AbstractRequestHandler> entry : handlers.entrySet()) {
                if (url.endsWith(entry.getKey())) {
                    handler = entry.getValue();
                    break;
                }
            }

            // Use default handler if no specific handler found
            if (handler == null) {
                handler = defaultHandler;
            }

            // Handle the request
            logger.info("Routing request to handler: {}", handler.getClass().getSimpleName());
            handler.handle(request, response);

        } catch (Exception e) {
            logger.error("Error handling request", e);
            response.setError(ReplyCode.NOTIMPLEMENTED, request.version);
        }
    }
}