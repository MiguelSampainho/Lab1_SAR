package com.sar.web.http;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author pedroamaral 
 * 
 * Class that stores all information about a HTTP request
 * Incomplete Version 24/25
 */
public class Request {
    private static final Logger logger = LoggerFactory.getLogger(Request.class);

    private final String clientAddress;
    private final int clientPort;
    private final int serverPort;


    public Headers headers; // stores the HTTP headers of the request
    public Properties cookies; //stores cookies received in the Cookie Headers
    private Properties postParameters; //stores POST parameters if request is a POST
    public String text;     //store possible contents in an HTTP request (for example POST contents)
    public String version;
    public String method;
    public String urlText;
    /** 
     * Creates a new instance of HTTPQuery
     * @param _UserInterface   log object
     * @param id    log id
     * @param LocalPort local HTTP server port
     */
    public Request(String clientAddress, int clientPort, int serverPort) {
        this.clientAddress = clientAddress;
        this.clientPort = clientPort;
        this.serverPort = serverPort;
        this.headers = new Headers();
        this.cookies = new Properties();
        this.postParameters = new Properties();
    }

    //Method to getClienAddress
    public String getClientAddress() {
        return clientAddress;
    }

    //Method to getClientPort
    public int getClientPort() {
        return clientPort;
    }

    // Cookie handling get cokkie header value and parse it in to the cookies properties
    public void parseCookies() {
        String cookieHeader = headers.getHeaderValue("Cookie");
        if (cookieHeader != null) {
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2) {
                    cookies.setProperty(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }

     /**
     * Get a header property value
     * @param hdrName   header name
     * @return          header value
     */
    public String getHeaderValue(String hdrName) {
        return headers.getHeaderValue(hdrName);
    }
    
    /**
     * Set a header property value
     * @param hdrName   header name
     * @param hdrVal    header value
     */
    public void setHeader(String hdrName, String hdrVal) {
        headers.setHeader(hdrName, hdrVal);
    }

    
    /** Returns the Cookie Properties object */
    public Properties getCookies () {
        return this.cookies;
    }
    
    public Properties getPostParameters() {
        return postParameters;
    }
    
    /**
     * Remove a header property name
     * @param hdrName   header name
     * @return true if successful
     */
    public boolean removeHeader(String hdrName) {
        return headers.removeHeader(hdrName);
    } 
}