package com.sar.web.handler;

 import com.sar.web.http.Request;
 import com.sar.web.http.Response;
 import com.sar.web.http.ReplyCode;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import java.io.File;
 import java.io.FileInputStream; // Keep if using direct file writing (though Response handles it now)
 import java.io.IOException;
 import java.io.OutputStream; // Keep if using direct file writing
 import java.io.PrintWriter; // Keep if using direct file writing
 import java.text.DateFormat;
 import java.text.SimpleDateFormat;
 import java.text.ParseException; // Added import
 import java.util.Date;
 import java.util.HashMap;
 import java.util.Locale;
 import java.util.Map;
 import java.util.Set; // Added import for Map iteration
 import java.util.TimeZone;

 public class StaticFileHandler extends AbstractRequestHandler {
     private static final Logger logger = LoggerFactory.getLogger(StaticFileHandler.class);
     private final String baseDirectory;
     private final String homeFileName;
     private final Map<String, String> mimeTypes;

     // Constructor
     public StaticFileHandler(String baseDirectory, String homeFileName) {
         this.baseDirectory = baseDirectory;
         this.homeFileName = homeFileName;
         this.mimeTypes = MIME_TYPES; // Initialize from static map
     }

     // Static block for MIME types
     private static final Map<String, String> MIME_TYPES = new HashMap<>();
     static {
         MIME_TYPES.put(".html", "text/html");
         MIME_TYPES.put(".htm", "text/html");
         MIME_TYPES.put(".css", "text/css");
         MIME_TYPES.put(".js", "text/javascript");
         MIME_TYPES.put(".jpg", "image/jpeg");
         MIME_TYPES.put(".jpeg", "image/jpeg");
         MIME_TYPES.put(".png", "image/png");
         MIME_TYPES.put(".gif", "image/gif");
         MIME_TYPES.put(".ico", "image/x-icon"); // Added favicon type
         // Add other necessary types
     }
     private static final String DEFAULT_MIME_TYPE = "application/octet-stream";


     @Override
     protected void handleGet(Request request, Response response) {
         // *** Force Logging to WARN level for this specific check ***
         String ifModifiedSinceHeader = request.headers.getHeaderValue("If-Modified-Since");
         // Use WARN to ensure this log appears regardless of logback.xml level (unless set higher than WARN)
         logger.warn(">>>> Checking for If-Modified-Since Header. Value retrieved: {}", ifModifiedSinceHeader);
         // ***

         String path = request.urlText;
         if ("/".equals(path)) {
             path = "/" + homeFileName;
         }

         // Ensure path separators are correct for the OS
         String fullPath = baseDirectory + path.replace('/', File.separatorChar);
         File file = new File(fullPath);

         try {
             if (file.exists() && file.isFile()) {
                 // --- If-Modified-Since Check START ---
                 // Use the variable already fetched above
                 if (ifModifiedSinceHeader != null && !ifModifiedSinceHeader.isEmpty()) {
                      // Force log level to WARN for this test
                     logger.warn(">>>> Received If-Modified-Since header: {}", ifModifiedSinceHeader);
                     try {
                         // Ensure correct date format pattern and Locale/TimeZone
                         DateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.UK);
                         httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                         Date headerDate = httpDateFormat.parse(ifModifiedSinceHeader);
                         logger.warn(">>>> Parsed header date: {} ({} ms)", headerDate, headerDate.getTime()); // WARN level

                         long fileLastModifiedMillis = file.lastModified();
                         logger.warn(">>>> File last modified: {} ms ({})", fileLastModifiedMillis, new Date(fileLastModifiedMillis)); // WARN level + Date

                         // Compare file's last modified time (truncate to seconds for comparison)
                         long fileLastModifiedSeconds = fileLastModifiedMillis / 1000;
                         long headerDateSeconds = headerDate.getTime() / 1000;
                         logger.warn(">>>> Comparing: File seconds {} <= Header seconds {}", fileLastModifiedSeconds, headerDateSeconds); // WARN level

                         if (fileLastModifiedSeconds <= headerDateSeconds) {
                             logger.warn(">>>> CONFIRMED: File not modified. Sending 304."); // WARN level
                             response.setCode(ReplyCode.NOTMODIFIED);
                             response.setVersion(request.version);
                             response.setDate(); // Set Date header for 304 response
                             // No body or other content-specific headers needed for 304
                             return; // Stop processing, response set above
                         } else {
                             logger.warn(">>>> CONFIRMED: File IS modified. Sending 200."); // WARN level
                         }
                     } catch (ParseException e) {
                         // Log parsing errors clearly
                         logger.error(">>>> CRITICAL: Could not parse If-Modified-Since header '{}': {}", ifModifiedSinceHeader, e.getMessage());
                          // Treat as if header wasn't usable, proceed to send 200 OK
                     }
                 } else {
                      // This will log if the header value was null or empty after retrieval attempt
                      logger.warn(">>>> No If-Modified-Since header received or header was empty."); // WARN level
                 }
                 // --- If-Modified-Since Check END ---

                 // If we reach here, send 200 OK
                 logger.info("Proceeding to send 200 OK for file: {}", fullPath); // Keep INFO
                 response.setCode(ReplyCode.OK);
                 response.setVersion(request.version);
                 prepareHeaders(response, file); // Sets Last-Modified, Content-Type etc.
                 response.setFile(file); // Associate the file with the response

             } else {
                 logger.warn("File not found: {}. Returning 404 error.", fullPath);
                 response.setCode(ReplyCode.NOTFOUND);
                 response.setVersion(request.version);
                 // Optional: Set a custom 404 page body
                 // response.setError(ReplyCode.NOTFOUND, request.version);
             }
         } catch (Exception e) {
             logger.error("Error handling GET request for file: {}", fullPath, e);
             response.setError(ReplyCode.BADREQ, request.version); // Consider 500 Internal Server Error
         }
     }

     @Override
     protected void handlePost(Request request, Response response) {
         logger.warn("POST method not supported for static file: {}", request.urlText);
         response.setError(ReplyCode.NOTIMPLEMENTED, request.version); // Or ReplyCode.METHODNOTALLOWED (405)
     }

     private String getMimeType(String path) {
         int dotIndex = path.lastIndexOf('.');
         if (dotIndex >= 0) { // Ensure dot is found
             String extension = path.substring(dotIndex).toLowerCase();
             return mimeTypes.getOrDefault(extension, DEFAULT_MIME_TYPE);
         }
         return DEFAULT_MIME_TYPE; // Default if no extension
     }

     /**
      * Sets appropriate headers for a static file response (e.g., Last-Modified, Content-Type, Content-Length).
      * Note: Date and Server headers are typically set by the Response class itself.
      * @param res The Response object to add headers to.
      * @param file The File being served.
      */
     private void prepareHeaders(Response res, File file) {
         // Ensure DateFormat pattern is correct and consistent
         DateFormat httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.UK);
         httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

         // Set Last-Modified header
         Date lastModified = new Date(file.lastModified());
         res.setHeader("Last-Modified", httpDateFormat.format(lastModified));

         // Set Content-Type header (including charset for text types)
         String contentType = getMimeType(file.getName());
         if (contentType.startsWith("text/")) {
              // Append charset only if it's a text type
             res.setHeader("Content-Type", contentType + "; charset=ISO-8859-1"); // As per project spec [cite: 25]
         } else {
             res.setHeader("Content-Type", contentType);
         }

         // Set Content-Length header
         res.setHeader("Content-Length", String.valueOf(file.length()));

         // Note: 'Content-Encoding' header is typically used for compression (gzip, deflate)
         // and not usually set to ISO-8859-1. The character set is part of Content-Type.
     }
 }