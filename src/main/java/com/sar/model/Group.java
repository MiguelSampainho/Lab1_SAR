// com.sar.model.Group.java
package com.sar.model;

import org.bson.types.ObjectId;
import java.time.Instant;
import java.util.Arrays;
import com.sar.server.Main;

public class Group {
    private ObjectId id;
    private String groupNumber;
    private int accessCount;
    private Member[] members;
    private String lastUpdate;
    private boolean counter;

   // Constructor
   public Group() {
    this.members = new Member[Main.GROUP_SIZE]; // Initialize with 3 members as per original implementation
    this.accessCount = 0;
    this.lastUpdate = Instant.now().toString();
   }
   // Getters and Setters
   public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getGroupNumber() {
        return groupNumber;
    }   

    public void setGroupNumber(String groupNumber) {
        this.groupNumber = groupNumber;
    }   
    
    public int getAccessCount() {
        return accessCount;
    }

    public boolean isCounter() {
        return counter;
    }
    
    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }   

    public void incrementAccessCount() {
        this.accessCount++;
    }

    public String getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(String lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public void setCounter(boolean counter) {
        this.counter = counter;
    }

    public Member[] getMembers() {
        return members;
    }   
    
    public void setMembers(Member[] members) {
        if (members.length != Main.GROUP_SIZE) {
            throw new IllegalArgumentException("Members array must be of size " + Main.GROUP_SIZE);
        }
        this.members = members;
    }
    
     // Utility methods
     public void updateLastUpdate() {
        this.lastUpdate = Instant.now().toString();
    }

    public void setMember(int index, String number, String name) {
        if (index >= 0 && index < Main.GROUP_SIZE) {
            members[index] = new Member(number, name);
        } else {
            throw new IllegalArgumentException("Index must be between 0 and " + (Main.GROUP_SIZE - 1));
        }
    }

    public Member getMember(int index) {
        if (index >= 0 && index < Main.GROUP_SIZE) {
            return members[index];
        }
        return null;
    }

    @Override
    public String toString() {
        return "Group{" +
                "id=" + id +
                ", groupNumber='" + groupNumber + '\'' +
                ", accessCount=" + accessCount +
                ", members=" + Arrays.toString(members) +
                ", lastUpdate='" + lastUpdate + '\'' +
                ", counter=" + counter +
                '}';
    }


    // Member class
    public static class Member {
        private String number;
        private String name;

        // getters and setters
        // Constructor
        public Member() {
        }

        public Member(String number, String name) {
            this.number = number;
            this.name = name;
        }

        // Getters and Setters
        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Member{" +
                    "number='" + number + '\'' +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

     // Builder pattern
     public static class Builder {
        private Group group;

        public Builder() {
            group = new Group();
        }

        public Builder groupNumber(String groupNumber) {
            group.setGroupNumber(groupNumber);
            return this;
        }

        public Builder member(int index, String number, String name) {
            if (index >= Main.GROUP_SIZE) {
                throw new IllegalArgumentException("Index cannot be larger than " + (Main.GROUP_SIZE - 1));
            }
            group.setMember(index, number, name);
            return this;
        }

        public Builder counter(boolean counter) {
            group.setCounter(counter);
            return this;
        }

        public Group build() {
            return group;
        }
    }
   
}