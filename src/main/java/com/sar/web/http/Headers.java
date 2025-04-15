package com.sar.web.http;

 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import java.util.*;
 import java.io.*;

 public class Headers {
     private static final Logger logger = LoggerFactory.getLogger(Headers.class);

     // Use TreeMap with CASE_INSENSITIVE_ORDER for keys
     private final Map<String, String> headers;

     public Headers() {
         // Initialize with a comparator that ignores case for keys
         this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
     }

     public void clear() {
         headers.clear();
     }

     /**
      * Store a header value. Existing headers with the same name (case-insensitive)
      * will be overwritten.
      * @param hdrName   header name
      * @param hdrVal    header value
      */
     public void setHeader(String hdrName, String hdrVal) {
         if (hdrName != null && hdrVal != null) {
             headers.put(hdrName.trim(), hdrVal.trim());
         }
     }

     /**
      * Returns the value of a header (case-insensitive).
      * @param hdrName   header name
      * @return  the header value or null if not found
      */
     public String getHeaderValue(String hdrName) {
         if (hdrName == null) {
             return null;
         }
         return headers.get(hdrName.trim());
     }

     /**
      * Reads Headers from request BufferedReader. Handles case-insensitivity.
      * @param reader   reader object
      */
     public void readHeaders(BufferedReader reader) throws IOException {
         String line;
         while ((line = reader.readLine()) != null) {
             if (line.trim().isEmpty()) { // Check for empty line to end headers
                 break;
             }

             int colonIndex = line.indexOf(":");
             if (colonIndex > 0) {
                 String name = line.substring(0, colonIndex).trim();
                 String value = line.substring(colonIndex + 1).trim();
                 // Store using setHeader which handles case-insensitivity via the TreeMap
                 setHeader(name, value);
                 logger.trace("Read header: {} = {}", name, value); // Use trace for verbose logging
             } else {
                 logger.warn("Malformed header line received: {}", line);
             }
         }
         logger.debug("Finished reading headers.");
     }

     /**
      * Writes all stored headers to the PrintStream.
      * @param writer The PrintStream to write to.
      */
      public void writeHeaders(PrintStream writer) {
         if (writer == null) {
             logger.error("PrintStream is null in writeHeaders");
             return;
         }
         // Iterate through the map entries and write them
         for (Map.Entry<String, String> entry : headers.entrySet()) {
             String headerLine = entry.getKey() + ": " + entry.getValue() + "\r\n";
             writer.print(headerLine);
             logger.trace("Writing header: {}", headerLine.trim());
         }
          logger.debug("Finished writing headers.");
     }


     /**
      * Removes a header (case-insensitive).
      * @param hdrName   header name
      * @return true if a header was removed, false otherwise
      */
     public boolean removeHeader(String hdrName) {
          if (hdrName == null) {
              return false;
          }
         // remove returns the previous value, check if it was null
         return headers.remove(hdrName.trim()) != null;
     }

     /**
      * Returns an enumeration of all header names (original case preserved if possible,
      * but iteration order depends on the Map implementation - TreeMap is sorted).
      * Note: Returning Enumeration for compatibility if needed, but Set is more modern.
      * @return an Enumeration object
      */
     public Enumeration<String> getAllHeaderNames() {
         // Properties uses Enumeration<Object>, but since we store String keys,
         // we can return Enumeration<String> using Collections.
         return Collections.enumeration(headers.keySet());
     }

      /**
      * Returns a Set of all header names.
      * @return a Set object containing header names
      */
     public Set<String> getHeaderNameSet() {
        return headers.keySet();
     }
 }