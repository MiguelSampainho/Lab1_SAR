package com.sar.web.http;

 import java.util.Properties;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import java.nio.charset.StandardCharsets; // Needed for URLDecoder
 import java.net.URLDecoder;             // Needed for URLDecoder

 public class Request {
     private static final Logger logger = LoggerFactory.getLogger(Request.class);

     private final String clientAddress;
     private final int clientPort;
     private final int serverPort;

     public Headers headers; // stores the HTTP headers of the request
     public Properties cookies; //stores cookies received in the Cookie Headers
     public Properties postParameters; //stores POST parameters if request is a POST
     public String text;     //store possible contents in an HTTP request (for example POST contents)
     public String version;
     public String method;
     public String urlText;

     public Request(String clientAddress, int clientPort, int serverPort) {
         this.clientAddress = clientAddress;
         this.clientPort = clientPort;
         this.serverPort = serverPort;
         this.headers = new Headers();        // Uses case-insensitive TreeMap now
         this.cookies = new Properties();     // Initialize cookies Properties
         this.postParameters = new Properties(); // Initialize POST parameters
     }

     // Getters
     public String getClientAddress() { return clientAddress; }
     public int getClientPort() { return clientPort; }
     public String getHeaderValue(String hdrName) { return headers.getHeaderValue(hdrName); }
     public Properties getCookies() { return this.cookies; }
     public Properties getPostParameters() { return postParameters; }

     // Setters/Removers (less common for Request)
     public void setHeader(String hdrName, String hdrVal) { headers.setHeader(hdrName, hdrVal); }
     public boolean removeHeader(String hdrName) { return headers.removeHeader(hdrName); }


     /**
      * Parses the 'Cookie' header from the request headers
      * and populates the cookies Properties object.
      */
     public void parseCookies() {
         String cookieHeader = headers.getHeaderValue("Cookie"); // Case-insensitive lookup
         this.cookies.clear(); // Clear any previous cookies for this request object

         if (cookieHeader != null && !cookieHeader.isEmpty()) {
             logger.trace("Parsing Cookie header: {}", cookieHeader);
             // Cookies are separated by "; " (semicolon and space)
             String[] cookiePairs = cookieHeader.split(";");

             for (String cookiePair : cookiePairs) {
                 String trimmedPair = cookiePair.trim();
                 int equalsIndex = trimmedPair.indexOf('=');

                 if (equalsIndex > 0) { // Ensure '=' is present and not the first char
                     String name = trimmedPair.substring(0, equalsIndex).trim();
                     String value = ""; // Default to empty string if no value part
                     if (equalsIndex < trimmedPair.length() - 1) {
                         value = trimmedPair.substring(equalsIndex + 1).trim();
                     }
                     if (!name.isEmpty()) { // Ensure cookie name is not empty
                        this.cookies.setProperty(name, value);
                        logger.trace("Stored cookie: [{}] = [{}]", name, value);
                     }
                 } else if (!trimmedPair.isEmpty()){
                     logger.trace("Found cookie part without value: {}", trimmedPair);
                 }
             }
         } else {
             logger.trace("No Cookie header found in request.");
         }
     }

 } // End of class