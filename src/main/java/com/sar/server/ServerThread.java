package com.sar.server;

import java.net.ServerSocket;
import java.net.Socket; 
import java.io.IOException; 
import com.sar.controller.HttpController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server Thread
 * @author pedroamaral
 */
public class ServerThread extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(ServerThread.class);
    private final Main httpServer;
    private final ServerSocket serverSock;
    private final HttpController httpController;
        
    public ServerThread(Main httpServer, ServerSocket serverSock, HttpController httpController) {
        this.httpServer = httpServer;
        this.serverSock = serverSock;
        this.httpController = httpController;
        setPriority(NORM_PRIORITY - 1);
    }
        
        public void wake_up () {
            this.interrupt ();
        }
        
        public void stop_thread () {
            httpServer.thread_ended();
            this.interrupt ();
        }
        
        @Override
        public void run () {
            logger.info (
                    "Server at port "+serverSock.getLocalPort ()+"\n"+ " started!");
            while ( true ) {
                try {
                    Socket client = serverSock.accept();
                    httpServer.thread_started();
                    
                    // Create and start connection thread
                    ConnectionThread t = new ConnectionThread(
                        httpServer, 
                        serverSock, 
                        client, 
                        httpController
                    );
                    t.start();
                } catch (IOException e) {
                    if (httpServer.active()) {
                        logger.error("Server thread IO error", e);
                    }
                    break;
                }
            }
        }
}
