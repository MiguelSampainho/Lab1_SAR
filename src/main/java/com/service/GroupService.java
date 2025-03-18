// com.sar.service.GroupService.java
package com.service;

import com.sar.model.Group;
import java.util.List;

public interface GroupService {
 // Core CRUD operations
 List<Group> getAllGroups();
 Group getGroup(String groupNumber);
 void saveGroup(String groupNumber, String[] numbers, String[] names, boolean counter);
 void deleteGroup(String groupNumber);
 
 // Business operations
 void incrementAccessCount(String groupNumber);
 String getLastUpdate(String groupNumber);
 String generateGroupHtml(); // Generates HTML table of all groups
 boolean groupExists(String groupNumber);
}

