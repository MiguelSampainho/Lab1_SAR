package config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoConfig {
    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);

    // Configuration constants (can be moved to properties file)
    //private static final String MONGO_HOST = System.getenv().getOrDefault("MONGO_HOST", "localhost");
    //private static final int MONGO_PORT = Integer.parseInt(System.getenv().getOrDefault("MONGO_PORT", "27017"));
    private static final String MONGO_DATABASE = System.getenv().getOrDefault("MONGO_DATABASE", "sardb");
   // private static final String MONGO_USER = System.getenv().getOrDefault("MONGO_USER", "root");
   // private static final String MONGO_PASSWORD = System.getenv().getOrDefault("MONGO_PASSWORD", "example");

    // Singleton instance of MongoClient
    private static MongoClient mongoClient;

    // Private constructor to prevent instantiation
    private MongoConfig() {}

    public static MongoClient getClient() {
        if (mongoClient == null) {
            mongoClient = createMongoClient();
        }
        return mongoClient;
    }

    private static MongoClient createMongoClient() {
        try {
            // Build the connection string
            String connectionString ="mongodb://127.0.0.1:27017/local"; 

            // Create MongoDB client settings
            MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .applyToConnectionPoolSettings(builder -> 
                    builder.maxSize(50)  // Maximum number of connections
                           .minSize(10)  // Minimum number of connections
                           .maxWaitTime(5000, java.util.concurrent.TimeUnit.MILLISECONDS)
                )
                .applyToSocketSettings(builder ->
                    builder.connectTimeout(5000, java.util.concurrent.TimeUnit.MILLISECONDS)
                           .readTimeout(5000, java.util.concurrent.TimeUnit.MILLISECONDS)
                )
                .build();

            // Create and return MongoDB client
            return MongoClients.create(settings);

        } catch (Exception e) {
            logger.error("Failed to create MongoDB client", e);
            throw new RuntimeException("Could not create MongoDB client", e);
        }
    }

    // Method to get database name
    public static String getDatabaseName() {
        return MONGO_DATABASE;
    }

    // Method to close client connection
    public static void closeClient() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
                mongoClient = null;
            } catch (Exception e) {
                logger.error("Error closing MongoDB client", e);
            }
        }
    }

    // Method to check database connection
    public static boolean testConnection() {
        try {
            getClient().listDatabaseNames().first();
            return true;
        } catch (Exception e) {
            logger.error("Failed to connect to MongoDB", e);
            return false;
        }
    }
}