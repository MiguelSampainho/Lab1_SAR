package com.sar.web.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.io.*;

/**
 *
 * @author pedroamaral
 */
public class Headers {
    private static final Logger logger = LoggerFactory.getLogger(Headers.class);

    public Properties headers;                            // Single value list of headers
    
     /**
     * Creates an empty list of headers
     * @param log log object
     */
    public Headers() {
        this.headers = new Properties();
    }
     /**
     * Clears the contents of the headers properties object
     */
    public void clear() {
        headers.clear();
    }
    
    /**
     * Store a header value; 
     * @param hdrName   header name
     * @param hdrVal    header value
     */
    public void setHeader(String hdrName, String hdrVal) {
        String storedHdrVal= headers.getProperty(hdrName);
        if (storedHdrVal == null) {
            headers.setProperty(hdrName, hdrVal);
        }       
    }

    /**
     * Returns the value of a property (returns the last one)
     * @param hdrName   header name
     * @return  the last header value
     */
    public String getHeaderValue(String hdrName) {
        return headers.getProperty(hdrName);
    }

    /**
     * Reads Headers from request BufferedReader (to complete)
     * @param reader   reader object
     */
    public void readHeaders(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }

            int colonIndex = line.indexOf(":");
            if (colonIndex > 0) {
                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                setHeader(name, value);
            } else {
                logger.warn("Error in header line: {}", line);
            }
        }
    }

    public void writeHeaders(PrintStream writer) {
        headers.stringPropertyNames().forEach(name -> 
        writer.print(name + ": " + headers.getProperty(name) + "\r\n"));
    }


    /**
     * Removes all the values of a header
     * @param hdrName   header name
     * @return true if a header was removed, false otherwise
     */
    public boolean removeHeader(String hdrName) {
        if (headers.containsKey(hdrName)) {
            headers.remove(hdrName);
            return true;
        } else
            return false;
    }
    
    /**
     * Returns an enumeration of all header names
     * @return an enumeration object
     */
    public Enumeration<Object> getAllHeaderNames() {
        return headers.keys();
    }
    
}
