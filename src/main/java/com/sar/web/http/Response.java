package com.sar.web.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import java.text.SimpleDateFormat;


public class Response {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Response.class);
    /**
     * Reply code information
     */
    public ReplyCode code;
    /**
     * Reply headers data
     */
    public Headers headers; // stores the HTTP headers for the response
    public ArrayList<String> setCookie;   // List to store Set-cookie header fields
    /**
     * Reply contents
     * They are stored either in a text buffer or in a file
     */
    public String text; // buffer with reply contents for dynamic API responses or server generated HTML code
    public File file;   // file used if text == null, for responses that contain a file  
    private final String serverName;
    /**
     * Creates a new instance of HTTPAnswer
      * @param server_name
     */
    public Response(String server_name) {
        this.code = new ReplyCode(); // code constains an instance of the HTTPReplyCode Class thar contains HTTP code values and an HTTP version field.
        this.headers = new Headers ();  // Headers object to store response HTTP headers  
        this.setCookie = new ArrayList<>(); // Array List of Strings to contain the Strings that make up the several values of the Set_Cookie Header. 
        this.serverName = server_name;
        /**
         * define Server header field name
         */
        this.headers.setHeader("Server", server_name);
        // data
    }

    public class DateUtil {
        public static String getHTTPDate(Date date) {
            SimpleDateFormat httpFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.UK);
            httpFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            return httpFormat.format(date);
        }
    }

    /* 
    Method to set the HTTP reply code of the answer
    */
    public void setCode(int _code) {
        code.setCode(_code);
    }
    /* 
    Method to set the HTTP version of the answer
    */
    public void setVersion(String v) {
        code.setVersion(v);
    }
     /* 
    Method to set an HTTP header of the answer
    */
    public void setHeader(String name, String value) {
        headers.setHeader(name, value);
    }
    /*
     * Method to set the static file to be sent in the reply
     */
    public void setFile(File file) {
        this.file = file;
    }
    /*
     * Method to set the text to be sent in the reply
     */
    public void setText(String text) {
        this.text = text;
    }
  /* 
    Method to add a cookie value to the list of cookies that are to be sent in Set-Cookie Headers
    */
    public void addCookie(String cookie) {
        setCookie.add(cookie);
    }

    /** Sets the headers needed in a reply with a static file content and fill
     * the file property with the File object of the static file to send
     * @param _f
     * @param mime_enc */
    public void setFileHeaders(File file, String contentType) {
        this.file = file;
        this.text = null;
     
        if(file != null && file.exists()){
            headers.setHeader("Content-Type", contentType);
            headers.setHeader("Content-Length", String.valueOf(file.length()));
            headers.setHeader("Last-Modified", DateUtil.getHTTPDate(new Date(file.lastModified())));
            headers.setHeader("Date", DateUtil.getHTTPDate(new Date()));
        } else {
            logger.warn("Ficheiro inválido ou inexistente em setFileHeaders()");
        }
        logger.debug("Headers definidos em setFileHeaders: {}", headers);
    }

    /** Sets the headers needed in a reply with a locally generated HTML string
     * (_text object) and fill the text property with the String object 
     * containing the HTML to send
     * @param _text */
    public void setTextHeaders(String text) {
        this.text = text;
        this.file = null;
       
        headers.setHeader("Content-Type", "text/html; charset=UTF-8");
        headers.setHeader("Content-Length", String.valueOf(text.length()));
        headers.setHeader("Connection", "keep-alive");
        headers.setHeader("Date", DateUtil.getHTTPDate(new Date()));

        logger.debug("Headers definidos em setTextHeaders: {}", headers);
    }

        /** Prepares an HTTP answer with an error code
     * @param _code
     * @param version */
    public void setError(int codeNumber, String version) {
        setVersion(version);
        setDate();
        code.setCode(codeNumber);
        String errorHtml = generateErrorPage(codeNumber);
        setTextHeaders(errorHtml);
    }

    private String generateErrorPage(int code) {
        return String.format(
            "<html><head><title>Error %d</title></head>" +
            "<body><h1>Error %d - %s</h1>" +
            "<p>Generated by %s</p></body></html>",
            code, code, ReplyCode.codeText(code), serverName
        );
    }

    /**
     * Returns the current value of the answer code
     * @return 
     */
    public int getCode() {
        return code.getCode();
    }

    /**
     * Returns an iterator over all header names
     * @return 
     */
    public Enumeration<Object> get_Iterator_parameter_names() {
        return headers.getAllHeaderNames();
    }

    /**
     * Returns the array list with all cookie Strings to use as values in Set-Cookie Headers
     * @return 
     */
    public ArrayList<String> getSetCookies() {
        return setCookie;
    }

    /** Sets the "Date" header field with the local date in HTTP format */
    void setDate() {
        DateFormat httpformat =
                new SimpleDateFormat("EE, d MMM yyyy HH:mm:ss zz", Locale.UK);
        httpformat.setTimeZone(TimeZone.getTimeZone("GMT"));
        headers.setHeader("Date", httpformat.format(new Date()));
    }


    /** Sends the HTTP reply to the client using 'pout' text device
     * @param TextPrinter
     * @param send_data indicates if data is present in the response or only headers
     * @param echo
     * @throws java.io.IOException */
    public void send_Answer(PrintStream TextPrinter) throws IOException {
        if (code.getCodeTxt() == null) {
            code.setCode(ReplyCode.BADREQ);
        }
        logger.info("Sending reply: {} {} {}", code.getVersion(), code.getCode(), code.getCodeTxt());
        TextPrinter.print(code.toString() + "\r\n");
        
        //Send all headers using the Headers object
        headers.writeHeaders(TextPrinter);
        /**
         * Check if there are cookies to send if so add the corresponding Set-Cookie Headers
         * Set-Cookies have to be sent manually using TextPrinter without using the Headers object 
         * since it uses a Properties Object to store the headers and there can be multiple Set-cookie headers 
         * and a Properties cannot have two Keys with the same value
         */
        //end of headers
        TextPrinter.print("\r\n");
        //write content if present
        if (text != null) {
                TextPrinter.print(text);
        } else if (file != null) {
            writeFile(TextPrinter);
            } else if ((code.getCode() != ReplyCode.NOTMODIFIED)&&(code.getCode() != ReplyCode.TMPREDIRECT)) {
                logger.error("Internal server error sending answer\n");
            }
    
        TextPrinter.flush();
    }

    private void writeFile(PrintStream TextPrinter){
        try (FileInputStream fin = new FileInputStream(file)) {
            byte [] data = new byte [fin.available()];
            fin.read( data );  // Read the entire file to buffer 'data'
            // IMPORTANT - Please modify this code to send a file chunk-by-chunk
            //             to avoid having CRASHES with BIG FILES
            logger.info("HTTPAnswer may fail for large files - please modify it");
            TextPrinter.write(data);
        }
        catch (IOException e ) {
            System.out.println( "I/O error  opeening FileInputStream " + e );
        }
    }
    
}
