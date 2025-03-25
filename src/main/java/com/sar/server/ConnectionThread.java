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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TimeZone;

public class ConnectionThread extends Thread  {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionThread.class);
    private final HttpController controller;

    private final Main HTTPServer;
    private final ServerSocket ServerSock;
    private final Socket client;
    private final DateFormat HttpDateFormat;
    
    /** Creates a new instance of httpThread */
    public ConnectionThread(Main HTTPServer, ServerSocket ServerSock, 
    Socket client, HttpController controller) {
        this.HTTPServer = HTTPServer;
        this.ServerSock = ServerSock;
        this.client = client;
        this.controller = controller;
        this.HttpDateFormat = new SimpleDateFormat("EE, d MMM yyyy HH:mm:ss zz", Locale.UK);
        this.HttpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        setPriority(NORM_PRIORITY - 1);
}
    

     /** Reads a new HTTP Request from the input steam in to a Request object
     * @param TextReader   input stream Buffered Reader coonected to client socket
     * @param echo  if true, echoes the received message to the screen
     * @return Request object containing the request received from the client, or null in case of error
     * @throws java.io.IOException 
     */
    public Request GetRequest (BufferedReader TextReader) throws IOException {
        // Get first line
        String request = TextReader.readLine( );  	// Reads the first line
        if (request == null) {
            logger.debug("Invalid request Connection closed");
            return null;
        }
        logger.info("Request: ", request);
        StringTokenizer st= new StringTokenizer(request);
        if (st.countTokens() != 3) {
           logger.debug("Invalid request received ", request);
           return null;  // Invalid request
        } 
         //create an object to store the http request
         Request req= new Request (client.getInetAddress ().getHostAddress (), client.getPort (), ServerSock.getLocalPort ());  
         req.method= st.nextToken();    // Store HTTP method
         req.urlText= st.nextToken();    // Store URL
         req.version= st.nextToken();  // Store HTTP version
     
        // read the remaining headers in to the headers property of the request object   
        req.headers.readHeaders(TextReader);
        
        // check if the Content-Length size is different than zero. If true read the body of the request (that can contain POST data)
        int clength= 0;
        try {
            String len= req.headers.getHeaderValue("Content-Length");
            if (len != null)
                clength= Integer.parseInt (len);
            else if (!TextReader.ready ())
                clength= 0;
        } catch (NumberFormatException e) {
            logger.error("Bad request\n");
            return null;
        }
        if (clength>0) {
            // Length is not 0 - read data to string
            String str= new String ();
            char [] cbuf= new char [clength];
            //the content is not formed by line ended with \n so it need to be read char by char
            int n, cnt= 0;
            while ((cnt<clength) && ((n= TextReader.read (cbuf)) > 0)) {
                str= str + new String (cbuf);
                cnt += n;
            }
            if (cnt != clength) {
                logger.info("Read request with {}} data bytes and Content-Length = {}} bytes\n",cnt, clength);
                return null;
            }
            req.text= str;
            logger.debug("Contents('"+req.text+"')\n");
        }

        return req;
    }    
   
    
     @Override
    public void run( ) {

        Response res= null;   // HTTP response object
        Request req = null;   //HTTP request object
        PrintStream TextPrinter= null;

        try {
            /*get the input and output Streams for the TCP connection and build
              a text (ASCII) reader (TextReader) and writer (TextPrinter) */
            InputStream in = client.getInputStream( );
            BufferedReader TextReader = new BufferedReader(
                    new InputStreamReader(in, "8859_1" ));
            OutputStream out = client.getOutputStream( );
            TextPrinter = new PrintStream(out, false, "8859_1");
            // Read and parse request
            req= GetRequest(TextReader); //reads the input http request if everything was read ok it returns true
            //Create response object. 
            res= new Response(HTTPServer.ServerName);
            // Let the controler (HttpContrller) handle the request and fill the response.
            controller.handleRequest(req, res);
            // Send response
            res.send_Answer(TextPrinter);

        } catch (Exception e) {
            logger.error("Error processing request", e);
            if (res != null) {
                res.setError(ReplyCode.BADREQ, req != null ? req.version : "HTTP/1.1");
            }
        } finally {
            cleanup(TextPrinter);
        }
    }   


    private void cleanup(PrintStream TextPrinter) {
        try {
            if (TextPrinter != null) TextPrinter.close();
            if (client != null) client.close();
        } catch (IOException e) {
            logger.error("Error during cleanup", e);
        } finally {
            HTTPServer.thread_ended();
            logger.debug("Connection closed for client: {}:{}", 
                client.getInetAddress().getHostAddress(), 
                client.getPort());
        }
    }

}
