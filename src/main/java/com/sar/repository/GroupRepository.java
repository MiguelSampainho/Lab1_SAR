package com.sar.repository;
import com.sar.model.Group;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Group data operations.
 * Defines all database operations that can be performed on Group entities.
 */
public interface GroupRepository {
    
    /**
     * Retrieves all groups from the database.
     * @return List of all groups
     */
    List<Group> findAll();

    /**
     * Finds a specific group by its group number.
     * @param groupNumber The unique identifier of the group
     * @return Optional containing the group if found, empty otherwise
     */
    Optional<Group> findByGroupNumber(String groupNumber);

    /**
     * Saves or updates a group in the database.
     * If the group exists, it will be updated; if not, it will be created.
     * @param group The group to save
     */
    void save(Group group);

    /**
     * Deletes a group from the database.
     * @param groupNumber The group number to delete
     */
    void delete(String groupNumber);

    /**
     * Increments the access count for a specific group.
     * @param groupNumber The group number to increment counter for
     */
    void incrementAccessCount(String groupNumber);

    /**
     * Gets the last update timestamp for a specific group.
     * @param groupNumber The group number to get last update for
     * @return The last update timestamp as a string
     */
    String getLastUpdate(String groupNumber);

    /**
     * Checks if a group exists in the database.
     * @param groupNumber The group number to check
     * @return true if the group exists, false otherwise
     */
    boolean exists(String groupNumber);

    /**
     * Gets the current access count for a specific group.
     * @param groupNumber The group number to get count for
     * @return The current access count
     */
    int getAccessCount(String groupNumber);

    /**
     * Updates the last access time for a specific group.
     * @param groupNumber The group number to update
     * @param timestamp The new timestamp
     */
    void updateLastAccess(String groupNumber, String timestamp);


    /**
     * Counts total number of groups in the database.
     * @return Total number of groups
     */
    long count();

    /**
     * Finds groups by member number.
     * @param memberNumber The member number to search for
     * @return List of groups containing the member
     */
    List<Group> findByMemberNumber(String memberNumber);

    /**
     * Deletes all groups from the database.
     * Use with caution!
     */
    void deleteAll();
}