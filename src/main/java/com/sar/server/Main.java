package com.sar.server;
//imports of the required classes for mongoDB connection
import com.mongodb.client.MongoClient;
import config.MongoConfig;
import com.sar.controller.HttpController;
//imports of the required classes for database operations
import com.sar.repository.GroupRepository;
import com.sar.repository.MongoGroupRepository;
import com.service.GroupService;
import com.service.GroupServiceImpl;
import com.sar.web.handler.ApiHandler;
import com.sar.web.handler.StaticFileHandler;

//imports of the required classes for the server
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
   
    //Static configuration
    public final String ServerName= "SAR Server by ----/-----";
    public final static String StaticFiles = "html";
    public final static String HOMEFILENAME = "index.htm";
    //Keep alive settings
    public final static boolean keepAlive = true;
    public final static int KeepAliveTime = 0; // set time in miliseconds to keep connection open
    //Authorization settings
    public final static boolean Authorization = false ;
    public final static String UserPass = "Username:Pass";
    public final static int GROUP_SIZE = 2;
    //port for Server socket serving HTTP requests
    public final static int HTTPport = 20000;
    //port for Server Socket serving HTTPs requests
    public final static int HTTPSport = 20043;
    static public SSLContext sslContext= null;
    final static int MaxAcceptLog= 10;  // Accepts up to 10 pending TCP connections
    
    // Instance components through dependency injection
    private final MongoClient mongoClient;
    private final GroupRepository groupRepository;
    private final GroupService groupService;
    private final ApiHandler apiHandler;
    private final StaticFileHandler staticFileHandler;
    private final HttpController httpController;
    
    // Server components
    private ServerThread MainThread= null;
    private ServerThread MainSecureThread = null; //https thread
    private ServerSocket server;
    private SSLServerSocket serverS;
    private int n_threads=0;
    
   public Main() {
       // Initialize components in proper order
       try {
        // 1. Initialize MongoDB connection
        logger.info("Initializing MongoDB connection...");
        this.mongoClient = initializeMongoClient();

        // 2. Initialize Repository
        logger.info("Initializing Group Repository...");
        this.groupRepository = initializeGroupRepository(this.mongoClient);

        // 3. Initialize Service
        logger.info("Initializing Group Service...");
        this.groupService = initializeGroupService(this.groupRepository);

        // 4. Initialize Handlers   
        logger.info("Initializing Static File Handler...");
        this.staticFileHandler = initializestaticFileHandler(StaticFiles, HOMEFILENAME);
        logger.info("Initializing API Handler...");
        this.apiHandler = initializeApiHandler(this.groupService);

        //5. Initialize Controller
        logger.info("Initializing HTTP Controller...");
        this.httpController = initializeHttpController(this.apiHandler, this.staticFileHandler);
        logger.info("All components initialized successfully");
   
    } catch (Exception e) {
        logger.error("Failed to initialize application components", e);
        throw new RuntimeException("Application initialization failed", e);
    }
    }

     // Component initialization methods
     private MongoClient initializeMongoClient() {
        try {
            if (!MongoConfig.testConnection()) {
                throw new RuntimeException("MongoDB connection test failed");
            }
            return MongoConfig.getClient();
        } catch (Exception e) {
            logger.error("Failed to initialize MongoDB client", e);
            throw new RuntimeException("MongoDB client initialization failed", e);
        }
    }

    private GroupRepository initializeGroupRepository(MongoClient mongoClient) {
        try {
            return new MongoGroupRepository(mongoClient);
        } catch (Exception e) {
            logger.error("Failed to initialize Group Repository", e);
            throw new RuntimeException("Repository initialization failed", e);
        }
    }

    private GroupService initializeGroupService(GroupRepository repository) {
        try {
            return new GroupServiceImpl(repository);
        } catch (Exception e) {
            logger.error("Failed to initialize Group Service", e);
            throw new RuntimeException("Service initialization failed", e);
        }
    }

    private ApiHandler initializeApiHandler(GroupService service) {
        try {
            return new ApiHandler(service);
        } catch (Exception e) {
            logger.error("Failed to initialize API Handler", e);
            throw new RuntimeException("Handler initialization failed", e);
        }
    }

    private StaticFileHandler initializestaticFileHandler(String staticFiles, String homeFileName) {
        try {
            return new StaticFileHandler(staticFiles, homeFileName);
        } catch (Exception e) {
            logger.error("Failed to initialize Static Handler", e);
            throw new RuntimeException("Handler initialization failed", e);
        }
    }

    private HttpController initializeHttpController(ApiHandler apiHandler, StaticFileHandler staticFileHandler) {
        try {
            return new HttpController(apiHandler, staticFileHandler);
        } catch (Exception e) {
            logger.error("Failed to initialize HTTP Controller", e);
            throw new RuntimeException("Controller initialization failed", e);
        }
    }
    
    // Server methods
    public void startServer() {
        try {
            // Initialize HTTP server
            logger.info("Starting HTTP server on port {}", HTTPport);
            server = new ServerSocket(HTTPport, MaxAcceptLog);

            // Initialize HTTPS server
            logger.info("Starting HTTPS server on port {}", HTTPSport);
            SSLServerSocketFactory sslSrvFact = sslContext.getServerSocketFactory();
            serverS = (SSLServerSocket) sslSrvFact.createServerSocket(HTTPSport);
            serverS.setNeedClientAuth(false);

            // Log local IP
            logger.info("Local IP: {}", InetAddress.getLocalHost().getHostAddress());

            // Start server threads
            MainThread = new ServerThread(this, server, httpController);
            MainSecureThread = new ServerThread(this, serverS, httpController);
            MainThread.start();
            MainSecureThread.start();

            logger.info("Servers started successfully");
        } catch (Exception e) {
            logger.error("Failed to start servers", e);
            throw new RuntimeException("Server startup failed", e);
        }
    }

    // Thread management methods
    public void thread_started() {
        if (MainThread != null) {
            n_threads++;
            logger.info("Thread started. Active threads: {}", n_threads);
        }
    }

    public void thread_ended() {
        if (MainThread != null) {
            n_threads--;
            logger.info("Thread ended. Active threads: {}", n_threads);
        }
    }
    
     // Getter methods
     public int getPortHTTP() {
        return HTTPport;
    }

    public int getPortHTTPS() {
        return HTTPSport;
    }

    public int getKeepAlive() {
        return 1000 * KeepAliveTime;
    }

    public String getStaticFilesUrl() {
        return StaticFiles;
    }

    public int active_connects() {
        return (MainThread == null && MainSecureThread == null) ? 0 : n_threads;
    }

    public boolean active() {
        return (MainThread != null || MainSecureThread != null);
    }
    
    // Resource cleanup
    protected void shutdown() {
        try {
            logger.info("Shutting down server and resources...");
            if (mongoClient != null) {
                mongoClient.close();
            }
            if (server != null) {
                server.close();
            }
            if (serverS != null) {
                serverS.close();
            }
            logger.info("Server shutdown complete");
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
    }

    /** Open up the KeyStore to obtain the Trusted Certificates.
     *  KeyStore is of type "JKS". Filename is "serverAppKeys.jks"
     *  and password is "myKeys".
     */
    private static void initContext() throws Exception {
        if (sslContext != null)
            return;
        
        try {
            // MAke sure that JSSE is available
           // Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
            
            // Create/initialize the SSLContext with key material
            char[] passphrase = "password".toCharArray(); // if the certificate was created with the password = "password"
            
            KeyStore ksKeys;
            try {
                // First initialize the key and trust material.
                ksKeys= KeyStore.getInstance("JKS");
            } catch (Exception e) {
                System.out.println("KeyStore.getInstance: "+e);
                return;
            }
            ksKeys.load(new FileInputStream("keystore"), passphrase);
            System.out.println("KsKeys has "+ksKeys.size()+" keys after load");
            
            // KeyManager's decide which key material to use.
            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance("SunX509");
            kmf.init(ksKeys, passphrase);
            System.out.println("KMfactory default alg.: "+KeyManagerFactory.getDefaultAlgorithm());
            
            
            sslContext = SSLContext.getInstance("TLSv1.2"); 
            sslContext.init(
                    kmf.getKeyManagers(), null /*tmf.getTrustManagers()*/, null);
            
        } catch (Exception e) {
            System.out.println("Failed to read keystore and trustfile.");
            e.printStackTrace();
            throw e;
        }
    }

    public static void main(String[] args) {
        try {
            // Initialize SSL context
            initContext();
            
            logger.info("Starting Server");
            Main HttpServer = new Main();
            HttpServer.startServer();
        } catch (Exception e) {
            logger.error("Server failed to start", e);
            System.exit(1);
        }
    }
}