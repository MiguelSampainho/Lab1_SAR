package com.sar.web.handler;
import com.sar.web.http.Response;
import com.sar.web.http.Request;
import com.sar.web.http.ReplyCode; // Import ReplyCode

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StaticFileHandler extends AbstractRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(StaticFileHandler.class);
    private final String baseDirectory;
    private final String homeFileName;
    private final Map<String, String> mimeTypes;
    private final Map<String, String> etagCache = new ConcurrentHashMap<>();

    // Constructor
    public StaticFileHandler(String baseDirectory, String homeFileName) {
        this.baseDirectory = baseDirectory;
        this.homeFileName = homeFileName;
        this.mimeTypes = MIME_TYPES;
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
        MIME_TYPES.put(".ico", "image/x-icon");
    }
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";


    @Override
    protected void handleGet(Request request, Response response) {
        String path = request.urlText;
        if ("/".equals(path) || path.isEmpty()) {
            path = "/" + homeFileName;
        }

        if (path.contains("..")) {
            logger.warn("Directory traversal attempt blocked: {}", path);
            response.setError(ReplyCode.BADREQ, request.version); // Use ReplyCode constant
            return;
        }

        String fullPath = baseDirectory + path.replace('/', File.separatorChar);
        File file = new File(fullPath);

        try {
            if (file.exists() && file.isFile() && file.canRead()) {

                String currentETag = generateETag(file);

                String ifNoneMatchHeader = request.headers.getHeaderValue("If-None-Match");
                if (ifNoneMatchHeader != null && !ifNoneMatchHeader.isEmpty()) {
                    if (ifNoneMatchHeader.trim().equals(currentETag)) {
                        logger.info("ETag matches for {}. Sending 304 Not Modified.", path);
                        response.setCode(ReplyCode.NOTMODIFIED); // Use ReplyCode constant
                        response.setVersion(request.version);
                        response.setDate();
                        response.setHeader("ETag", currentETag);
                        return;
                    } else {
                        logger.debug("ETag mismatch for {}. Header: '{}', File ETag: '{}'", path, ifNoneMatchHeader, currentETag);
                    }
                }

                String ifModifiedSinceHeader = request.headers.getHeaderValue("If-Modified-Since");
                if (ifModifiedSinceHeader != null && !ifModifiedSinceHeader.isEmpty()) {
                    try {
                        DateFormat httpDateFormat = Response.DateUtil.getHTTPDateFormatter();
                        Date headerDate = httpDateFormat.parse(ifModifiedSinceHeader);
                        long fileLastModifiedMillis = file.lastModified();
                        long fileLastModifiedSeconds = fileLastModifiedMillis / 1000;
                        long headerDateSeconds = headerDate.getTime() / 1000;

                        if (fileLastModifiedSeconds <= headerDateSeconds) {
                            logger.info("File not modified since {}. Sending 304 Not Modified.", ifModifiedSinceHeader);
                            response.setCode(ReplyCode.NOTMODIFIED); // Use ReplyCode constant
                            response.setVersion(request.version);
                            response.setDate();
                            response.setHeader("ETag", currentETag);
                            return;
                        } else {
                             logger.debug("File IS modified since {}.", ifModifiedSinceHeader);
                        }
                    } catch (ParseException e) {
                        logger.warn("Could not parse If-Modified-Since header '{}': {}", ifModifiedSinceHeader, e.getMessage());
                    }
                }

                logger.info("Sending 200 OK for file: {}", fullPath);
                response.setCode(ReplyCode.OK); // Use ReplyCode constant
                response.setVersion(request.version);
                prepareHeaders(response, file, currentETag);
                response.setFile(file);

            } else {
                logger.warn("Static file not found or not readable: {}. Returning 404.", fullPath);
                response.setError(ReplyCode.NOTFOUND, request.version); // Use ReplyCode constant
            }
        } catch (Exception e) {
            logger.error("Error handling GET request for static file: {}", fullPath, e);
            response.setError(ReplyCode.BADREQ, request.version); // Use ReplyCode constant
        }
    }

    @Override
    protected void handlePost(Request request, Response response) {
        logger.warn("POST method not supported for static file: {}", request.urlText);
        // *** Corrected to use ReplyCode.METHODNOTALLOWED ***
        response.setError(ReplyCode.METHODNOTALLOWED, request.version);
        response.setHeader("Allow", "GET, HEAD");
    }

    private String generateETag(File file) {
       String filePath = file.getAbsolutePath();
       String cachedEtag = etagCache.get(filePath);
       String currentTagValue = createETagValue(file);

       if (cachedEtag != null) {
           if (cachedEtag.equals(currentTagValue)) {
                logger.trace("ETag cache hit for: {}", filePath);
                return cachedEtag;
           } else {
               logger.debug("ETag cache invalid for: {}, regenerating.", filePath);
           }
       }
       etagCache.put(filePath, currentTagValue);
       logger.trace("Generated and cached ETag for {}: {}", filePath, currentTagValue);
       return currentTagValue;
    }

    private String createETagValue(File file) {
        long lastModified = file.lastModified();
        long length = file.length();
        return "\"" + Long.toHexString(lastModified) + "-" + Long.toHexString(length) + "\"";
    }

    private String getMimeType(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex >= 0) {
            // Use Locale.ENGLISH for consistent lowercasing regardless of system locale
            String extension = path.substring(dotIndex).toLowerCase(Locale.ENGLISH);
            return mimeTypes.getOrDefault(extension, DEFAULT_MIME_TYPE);
        }
        return DEFAULT_MIME_TYPE;
    }

    private void prepareHeaders(Response res, File file, String etag) {
        DateFormat httpDateFormat = Response.DateUtil.getHTTPDateFormatter();
        Date lastModified = new Date(file.lastModified());
        res.setHeader("Last-Modified", httpDateFormat.format(lastModified));

        if (etag != null) {
           res.setHeader("ETag", etag);
        }

        String contentType = getMimeType(file.getName());
        if (contentType.startsWith("text/")) {
            res.setHeader("Content-Type", contentType + "; charset=ISO-8859-1");
        } else {
            res.setHeader("Content-Type", contentType);
        }

        res.setHeader("Content-Length", String.valueOf(file.length()));
        res.setHeader("Accept-Ranges", "bytes");
        res.setHeader("Cache-Control", "public, max-age=3600");
    }

} // End of class
