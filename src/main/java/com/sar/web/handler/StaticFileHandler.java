package com.sar.web.handler;

import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class StaticFileHandler extends AbstractRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(StaticFileHandler.class);
    private final String baseDirectory;
    private final String homeFileName;
    private final Map<String, String> mimeTypes;

    public StaticFileHandler(String baseDirectory, String homeFileName) {
        this.baseDirectory = baseDirectory;
        this.homeFileName = homeFileName;
        this.mimeTypes = MIME_TYPES;
    }

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
    }

    @Override
    protected void handleGet(Request request, Response response) {
        String path = request.urlText;
        if (path.equals("/")) {
            path = "/" + homeFileName;
        }

        String fullPath = baseDirectory + path;
        File file = new File(fullPath);

        try {
            if (file.exists() && file.isFile()) {
                response.setCode(ReplyCode.OK);
                response.setVersion(request.version);
                // set file headers
                prepareHeaders(response, file);
                response.setFile(file);
                logger.info("Serving file: {}", fullPath);
            } else {
                logger.warn("File not found: {}. Returning 404 error.", fullPath);
                response.setCode(ReplyCode.NOTFOUND);
                response.setVersion(request.version);
            }
        } catch (Exception e) {
            logger.error("Error handling GET request for file: {}", fullPath, e);
            response.setError(ReplyCode.BADREQ, request.version);
        }
    }

    @Override
    protected void handlePost(Request request, Response response) {
        // Static files don't handle POST requests
        logger.error("StaticFileHandler does not handle POST requests.");
        response.setError(ReplyCode.NOTIMPLEMENTED, request.version);
    }

    private String getMimeType(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = path.substring(dotIndex).toLowerCase();
            return mimeTypes.getOrDefault(extension, DEFAULT_MIME_TYPE);
        }
        return DEFAULT_MIME_TYPE;
    }

    private void prepareHeaders(Response res, File file) {
        // Date header
        DateFormat httpDateFormat = new SimpleDateFormat("EE, d MMM yyyy HH:mm:ss z", Locale.UK);
        httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        res.setHeader("Date", httpDateFormat.format(new Date()));

        // Server header
        res.setHeader("Server", "MyHTTPServer"); // Or use the server name

        // Last-Modified header
        Date lastModified = new Date(file.lastModified());
        res.setHeader("Last-Modified", httpDateFormat.format(lastModified));

        // Content-Type header
        String contentType = getMimeType(file.getName());
        res.setHeader("Content-Type", contentType);

        // Content-Length header
        res.setHeader("Content-Length", String.valueOf(file.length()));

        // Content-Encoding header
        // Assuming all files are encoded in ISO-8859-1 as per the instructions
        res.setHeader("Content-Encoding", "ISO-8859-1");
    }
}