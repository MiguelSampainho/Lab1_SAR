
package com.sar.repository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.ReplaceOptions;
import com.sar.model.Group;
import com.sar.model.Group.Member;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.sar.server.Main;

// implementation of GroupRepository interface using the MongoDB Java driver
public class MongoGroupRepository implements GroupRepository {
    private final MongoCollection<Document> collection;
    private static final Logger logger = LoggerFactory.getLogger(MongoGroupRepository.class);

    public MongoGroupRepository(MongoClient mongoClient) {
        // Get the groups collection from MongoDB
        this.collection = mongoClient
            .getDatabase("sardb")
            .getCollection("groups");
    }

    @Override
    public List<Group> findAll() {
        List<Group> groups = new ArrayList<>();
        try {
            // Find all documents and convert each to a Group object
            collection.find().forEach(doc -> 
                groups.add(documentToGroup(doc)));
            return groups;
        } catch (Exception e) {
            logger.error("Error finding all groups", e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public Optional<Group> findByGroupNumber(String groupNumber) {
        try {
            // Find document by groupNumber
            Document doc = collection.find(
                Filters.eq("groupNumber", groupNumber)
            ).first();
            
            return Optional.ofNullable(doc)
                .map(this::documentToGroup);
        } catch (Exception e) {
            logger.error("Error finding group: " + groupNumber, e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
public List<Group> findByMemberNumber(String memberNumber) {
    List<Group> groups = new ArrayList<>();
    try {
        // Find all groups that contain a member with the specified number
        collection.find(
            Filters.elemMatch("members", 
                Filters.eq("number", memberNumber))
        ).forEach(doc -> 
            groups.add(documentToGroup(doc)));
        return groups;
    } catch (Exception e) {
        logger.error("Error finding groups by member number: " + memberNumber, e);
        throw new RuntimeException("Database error", e);
    }
}

@Override
public boolean exists(String groupNumber) {
    try {
        return collection.countDocuments(Filters.eq("groupNumber", groupNumber)) > 0;
    } catch (Exception e) {
        logger.error("Error checking if group exists: " + groupNumber, e);
        throw new RuntimeException("Database error", e);
    }
}

@Override
public int getAccessCount(String groupNumber) {
    try {
        Document doc = collection.find(Filters.eq("groupNumber", groupNumber)).first();
        return doc != null ? doc.getInteger("accessCount", 0) : 0;
    } catch (Exception e) {
        logger.error("Error getting access count for group: " + groupNumber, e);
        throw new RuntimeException("Database error", e);
    }
}

@Override
public void updateLastAccess(String groupNumber, String timestamp) {
    try {
        collection.updateOne(
            Filters.eq("groupNumber", groupNumber),
            Updates.set("lastUpdate", timestamp)
        );
    } catch (Exception e) {
        logger.error("Error updating last access for group: " + groupNumber, e);
        throw new RuntimeException("Database error", e);
    }
}

@Override
public long count() {
    try {
        return collection.countDocuments();
    } catch (Exception e) {
        logger.error("Error counting groups", e);
        throw new RuntimeException("Database error", e);
    }
}
    @Override
    public void save(Group group) {
        try {
            Document doc = groupToDocument(group);
            // Replace document if exists, insert if doesn't
            collection.replaceOne(
                Filters.eq("groupNumber", group.getGroupNumber()),
                doc,
                new ReplaceOptions().upsert(true)
            );
        } catch (Exception e) {
            logger.error("Error saving group: " + group.getGroupNumber(), e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public void delete(String groupNumber) {
        try {
            collection.deleteOne(
                Filters.eq("groupNumber", groupNumber)
            );
        } catch (Exception e) {
            logger.error("Error deleting group: " + groupNumber, e);
            throw new RuntimeException("Database error", e);
        }
    }

@Override
public void deleteAll() {
    try {
        collection.deleteMany(new Document());
        logger.info("All groups deleted from database");
    } catch (Exception e) {
        logger.error("Error deleting all groups", e);
        throw new RuntimeException("Database error", e);
    }
}

    @Override
    public void incrementAccessCount(String groupNumber) {
        try {
            collection.updateOne(
                Filters.eq("groupNumber", groupNumber),
                Updates.inc("accessCount", 1)
            );
        } catch (Exception e) {
            logger.error("Error incrementing access count for group: " + groupNumber, e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public String getLastUpdate(String groupNumber) {
        try {
            Document doc = collection.find(
                Filters.eq("groupNumber", groupNumber)
            ).first();
            
            return doc != null ? doc.getString("lastUpdate") : null;
        } catch (Exception e) {
            logger.error("Error getting last update for group: " + groupNumber, e);
            throw new RuntimeException("Database error", e);
        }
    }

    // Convert MongoDB Document to Group object
    private Group documentToGroup(Document doc) {
        Group group = new Group();
        group.setGroupNumber(doc.getString("groupNumber"));
        group.setAccessCount(doc.getInteger("accessCount", 0));
        group.setLastUpdate(doc.getString("lastUpdate"));
        group.setCounter(doc.getBoolean("counter", false));

        // Convert members array
        List<Document> membersDoc = doc.getList("members", Document.class);
        for (int i = 0; i < Main.GROUP_SIZE; i++) {
            if (i < membersDoc.size()) {
                Document memberDoc = membersDoc.get(i);
                group.setMember(
                    i,
                    memberDoc.getString("number"),
                    memberDoc.getString("name")
                );
            }
        }

        return group;
    }

    // Convert Group object to MongoDB Document
    private Document groupToDocument(Group group) {
        Document doc = new Document();
        doc.put("groupNumber", group.getGroupNumber());
        doc.put("accessCount", group.getAccessCount());
        doc.put("lastUpdate", group.getLastUpdate());
        doc.put("counter", group.isCounter());

        // Convert members array
        List<Document> members = new ArrayList<>();
        for (Member member : group.getMembers()) {
            if (member != null) {
                Document memberDoc = new Document();
                memberDoc.put("number", member.getNumber());
                memberDoc.put("name", member.getName());
                members.add(memberDoc);
            }
        }
        doc.put("members", members);

        return doc;
    }
}