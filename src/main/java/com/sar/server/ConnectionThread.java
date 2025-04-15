package com.sar.server;

import com.sar.controller.HttpController;
import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.StringTokenizer;
import java.net.URLDecoder;


public class ConnectionThread extends Thread  {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionThread.class);
    private final HttpController controller;

    private final Main HTTPServer;
    private final ServerSocket ServerSock; // Can be SSLServerSocket or ServerSocket
    private final Socket client;

    /** Creates a new instance of ConnectionThread */
    public ConnectionThread(Main HTTPServer, ServerSocket ServerSock,
    Socket client, HttpController controller) {
        this.HTTPServer = HTTPServer;
        this.ServerSock = ServerSock;
        this.client = client;
        this.controller = controller;
        setPriority(NORM_PRIORITY - 1);
    }

    /** Reads a new HTTP Request from the input stream into a Request object */
    public Request GetRequest(BufferedReader TextReader) throws IOException {
        String request = TextReader.readLine();
        if (request == null) {
            logger.warn("Received invalid/null request. Connection seems closed by client or timed out.");
            return null;
        }

        logger.info("Request Received: {}", request);
        StringTokenizer st = new StringTokenizer(request);
        if (st.countTokens() != 3) {
            logger.warn("Invalid request line format: {}", request);
            return null;
        }

        Request req = new Request(client.getInetAddress().getHostAddress(), client.getPort(), ServerSock.getLocalPort());
        req.method = st.nextToken();
        req.urlText = st.nextToken();
        req.version = st.nextToken();

        // Read Headers
        try {
           req.headers.readHeaders(TextReader);
        } catch (IOException e) {
            logger.error("IOException while reading headers: {}", e.getMessage());
            return null;
        }

        // Parse Cookies
        req.parseCookies();
        if (!req.cookies.isEmpty()) {
            logger.debug("Parsed cookies found: {}", req.cookies.stringPropertyNames());
        }

        // Handle Content-Length and POST body
        int clength = 0;
        String contentLengthHeader = req.headers.getHeaderValue("Content-Length");
        if (contentLengthHeader != null) {
            try {
                clength = Integer.parseInt(contentLengthHeader.trim());
            } catch (NumberFormatException e) {
                logger.error("Bad request - Invalid Content-Length format: {}", contentLengthHeader);
                return null;
            }
        }

        if (clength > 0) {
            if (req.method.equalsIgnoreCase("POST")) {
                StringBuilder str = new StringBuilder(clength);
                char[] cbuf = new char[1024];
                int n, cnt = 0;
                try {
                    while (cnt < clength && (n = TextReader.read(cbuf, 0, Math.min(cbuf.length, clength - cnt))) != -1) {
                        str.append(cbuf, 0, n);
                        cnt += n;
                        if (cnt >= clength) break;
                    }
                } catch (IOException e) {
                    logger.error("IOException while reading request body: {}", e.getMessage());
                    return null;
                }
                if (cnt != clength) {
                    logger.warn("Read POST data length mismatch: expected {}, got {}.", clength, cnt);
                }
                req.text = str.toString();
                if ("application/x-www-form-urlencoded".equalsIgnoreCase(req.headers.getHeaderValue("Content-Type"))) {
                    req.getPostParameters().putAll(parseUrlEncoded(req.text));
                }
                logger.debug("Request Body Contents received ({} bytes)", cnt);
            } else {
                logger.warn("Received request with Content-Length but method is not POST (Method: {}). Body ignored.", req.method);
            }
        }
        return req;
    }

    // Helper method to parse application/x-www-form-urlencoded data
    private Properties parseUrlEncoded(String data) {
       Properties params = new Properties();
       if (data != null && !data.isEmpty()) {
           try {
               String[] pairs = data.split("&");
               for (String pair : pairs) {
                   int idx = pair.indexOf("=");
                   if (idx >= 0) {
                       String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                       String value = (idx < pair.length() - 1) ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name()) : "";
                       params.setProperty(key, value);
                   } else if (pair.length() > 0) {
                        String key = URLDecoder.decode(pair, StandardCharsets.UTF_8.name());
                        params.setProperty(key, "");
                   }
               }
           } catch (Exception e) {
               logger.error("Error parsing URL encoded POST parameters", e);
           }
       }
       return params;
    }

    @Override
    public void run() {
        // Use try-with-resources for automatic closing of socket and streams
        try (Socket clientSocket = this.client;
             InputStream in = clientSocket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
             OutputStream out = clientSocket.getOutputStream();
             PrintStream writer = new PrintStream(out, true, StandardCharsets.UTF_8.name())) // Use UTF-8 for response
        {

            boolean keepAlive = true;
            logger.debug("Connection accepted from {}:{}. Starting keep-alive loop.", clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort());

            while (keepAlive && !clientSocket.isClosed() && !Thread.currentThread().isInterrupted()) {
                logger.debug("Top of keep-alive loop. Waiting for request...");
                Request req = GetRequest(reader);

                if (req == null) {
                    keepAlive = false;
                    logger.warn("GetRequest returned null, breaking keep-alive loop.");
                    break;
                }

                logger.info("Processing Request: {} {} {}", req.method, req.urlText, req.version);
                Response resp = new Response(HTTPServer.ServerName);
                resp.setVersion(req.version);

                boolean proceedWithRequest = true;
                boolean authorized = false;
                boolean authorizedByCookie = false;

                // --- 1. Handle HTTP to HTTPS Redirect ---
                if (!(ServerSock instanceof javax.net.ssl.SSLServerSocket) &&
                     ServerSock.getLocalPort() == Main.HTTPport &&
                     Main.Authorization) {

                   if ("HTTP/1.1".equals(req.version)) {
                       logger.info("Redirecting HTTP request to HTTPS for URL: {}", req.urlText);
                       resp.setCode(ReplyCode.TMPREDIRECT);
                       String host = req.headers.getHeaderValue("Host");
                       host = (host != null && host.contains(":")) ? host.substring(0, host.indexOf(":")) : (host != null ? host : "localhost");
                       resp.setHeader("Location", "https://" + host + ":" + Main.HTTPSport + req.urlText);
                       resp.setHeader("Connection", "close");
                       resp.setHeader("Content-Length", "0");
                       resp.setDate();
                       resp.send_Answer(writer);
                   } else {
                        logger.warn("Received non-HTTP/1.1 request ({}) on HTTP port. Sending 400.", req.version);
                        resp.setError(ReplyCode.BADREQ, req.version != null ? req.version : "HTTP/1.0");
                        resp.send_Answer(writer);
                   }
                   keepAlive = false;
                   proceedWithRequest = false;
                }
                // --- 2. Handle Authorization for HTTPS ---
                else if (proceedWithRequest && Main.Authorization) {
                    logger.debug("Authorization required. Checking first for cookie...");
                    String cookieValue = req.cookies.getProperty("SARAuth");

                    if ("Validated".equals(cookieValue)) {
                        authorized = true;
                        authorizedByCookie = true;
                        logger.info("Authorization successful via Cookie.");
                    }

                    if (!authorized) {
                        logger.debug("Cookie not found or invalid. Checking Authorization header...");
                        String authHeader = req.headers.getHeaderValue("Authorization");

                        if (authHeader == null || !authHeader.toLowerCase().startsWith("basic ")) {
                            logger.warn("Authorization header missing or not Basic. Sending 401.");
                            sendUnauthorizedResponse(resp, writer, req.version);
                            keepAlive = false;
                            proceedWithRequest = false;
                        } else {
                            try {
                                String base64Credentials = authHeader.substring("Basic".length()).trim();
                                byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
                                String credentials = new String(credDecoded, StandardCharsets.UTF_8);
                                final String[] values = credentials.split(":", 2);

                                if (values.length == 2 && (values[0] + ":" + values[1]).equals(Main.UserPass)) {
                                    logger.info("Authorization successful via Header for user: {}", values[0]);
                                    authorized = true;
                                    authorizedByCookie = false;
                                    String cookieString = "SARAuth=Validated; Path=/; Secure; HttpOnly";
                                    resp.addSetCookieHeader(cookieString);
                                    logger.info("Set-Cookie header added for successful login via Header.");
                                } else {
                                    logger.warn("Authorization failed via Header. Incorrect credentials provided. Sending 401.");
                                    sendUnauthorizedResponse(resp, writer, req.version);
                                    keepAlive = false;
                                    proceedWithRequest = false;
                                }
                            } catch (Exception e) {
                                logger.error("Error decoding/processing Authorization header: {}", e.getMessage(), e);
                                sendUnauthorizedResponse(resp, writer, req.version);
                                keepAlive = false;
                                proceedWithRequest = false;
                            }
                        }
                    }

                    if (authorized) {
                       proceedWithRequest = true;
                       if (authorizedByCookie) {
                            String cookieString = "SARAuth=Validated; Path=/; Secure; HttpOnly";
                            resp.addSetCookieHeader(cookieString);
                            logger.info("Set-Cookie header added for successful authorization (by Cookie).");
                       }
                    }
                } // End Authorization Check block

                // --- 3. Process Request if Allowed ---
                if (proceedWithRequest) {
                    if (Main.Authorization) {
                       logger.debug("Request authorized by {}. Handling request...", (authorizedByCookie ? "Cookie" : "Header"));
                    } else {
                        logger.debug("Authorization disabled. Handling request...");
                    }

                    try {
                        controller.handleRequest(req, resp);
                        logger.debug("controller.handleRequest completed with status {}", resp.getCode());
                    } catch (Exception e) {
                         logger.error("Error during controller.handleRequest: {}", e.getMessage(), e);
                         resp.setError(ReplyCode.BADREQ, req.version);
                         keepAlive = false;
                    }

                    // --- Determine Keep-Alive ---
                    String connectionHeader = req.headers.getHeaderValue("Connection");
                    if ("close".equalsIgnoreCase(connectionHeader) ||
                        !"HTTP/1.1".equals(req.version) ||
                        resp.getCode() >= 400 ||
                        resp.getCode() == ReplyCode.TMPREDIRECT )
                    {
                         logger.debug("Setting Connection: close (ReqHdr: {}, Ver: {}, RespCode: {})", connectionHeader, req.version, resp.getCode());
                         keepAlive = false;
                         resp.setHeader("Connection", "close");
                    } else {
                         logger.debug("Setting Connection: keep-alive (Default for HTTP/1.1)");
                         keepAlive = true;
                         resp.setHeader("Connection", "keep-alive");
                    }
                    resp.setDate();

                    // --- Send the Response ---
                    try {
                       resp.send_Answer(writer);
                       logger.debug("Response with status {} sent.", resp.getCode());

                       // Explicitly flush the underlying OutputStream after PrintStream operations
                       try {
                            out.flush();
                            logger.debug("Underlying OutputStream flushed explicitly after send_Answer.");
                       } catch (IOException flushEx) {
                            logger.error("Error flushing underlying OutputStream: {}", flushEx.getMessage());
                            keepAlive = false; // Force close if flush fails
                       }

                    } catch (IOException sendError) {
                        logger.error("IOException during send_Answer: {}", sendError.getMessage());
                        keepAlive = false;
                    }
                } else {
                    logger.warn("Request processing skipped (redirected or unauthorized). keepAlive={}", keepAlive);
                }

                // Final check on keepAlive status
                if (!keepAlive) {
                    logger.warn("KeepAlive is false. Preparing to break connection loop for client {}:{}.", clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort());
                    break; // Exit the while loop
                } else {
                    logger.debug("KeepAlive is true. Continuing connection loop.");
                }

            } // End of while(keepAlive) loop
            logger.debug("Exited keep-alive loop for client {}:{}.", clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort());

        } catch (IOException e) {
            String message = e.getMessage();
            if (message != null && (message.contains("Socket closed") || message.contains("Connection reset") || message.contains("Broken pipe") || message.contains("SSLHandshakeException"))) {
               logger.warn("I/O Error in ConnectionThread (client likely disconnected or SSL issue): {}", message);
            } else {
               logger.error("I/O Error in ConnectionThread run method: {}", message, e);
            }
        } catch (Exception e) {
            logger.error("Unexpected error in ConnectionThread run method: {}", e.getMessage(), e);
        } finally {
            // try-with-resources handles closing. Just notify main server.
            HTTPServer.thread_ended();
            logger.info("ConnectionThread finished for client {}:{}.", client.getInetAddress().getHostAddress(), client.getPort());
        }
    } // End of run() method


    /** Helper method to send a 401 Unauthorized response */
    private void sendUnauthorizedResponse(Response resp, PrintStream writer, String version) throws IOException {
         logger.warn("Calling sendUnauthorizedResponse to send 401");
         resp.setCode(ReplyCode.UNAUTHORIZED);
         String effectiveVersion = version != null ? version : "HTTP/1.1";
         resp.setVersion(effectiveVersion);
         resp.setHeader("WWW-Authenticate", "Basic realm=\"SAR Server Restricted Area\"");
         resp.setError(ReplyCode.UNAUTHORIZED, effectiveVersion);
         resp.send_Answer(writer);
         logger.debug("401 Unauthorized response sent.");
    }

} // End of class
