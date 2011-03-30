/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.olio.workload.driver.operations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.olio.workload.driver.common.DBConnectionFactory;
import org.apache.olio.workload.driver.common.Message.MESSAGE;
import org.apache.olio.workload.driver.common.Operatable;

/**
 *
 * @author liang
 */
public class AddEvent implements Operatable {

    // Strings
    private static final String INSERT_ADDRESSES = "INSERT INTO `addresses` "
            + "(`city`, `zip`, `latitude`, `country`, `street1`, `street2`, `"
            + "longitude`, `state`) "
            + "VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_IMAGES1 = "SELECT `images`.id FROM `images` "
            + "WHERE (`images`.`filename` = BINARY ?)  LIMIT 1";
    private static final String INSERT_IMAGES1 = "INSERT INTO `images` "
            + "(`size`, `content_type`, `thumbnail`, `filename`, `height`, "
            + "`parent_id`, `width`) "
            + "VALUES(669128, 'image/jpeg; charset=ISO-8859-1', NULL, ?, 1268, "
            + "NULL, 1690)";
    private static final String SELECT_IMAGES2 = "SELECT * FROM `images` "
            + "WHERE (`images`.`parent_id` = ? AND `images`.`thumbnail` = 'thumb')  LIMIT 1";
    private static final String SELECT_IMAGES3 = "SELECT `images`.id FROM `images` "
            + "WHERE (`images`.`filename` = BINARY ?)  LIMIT 1";
    private static final String INSERT_IMAGES2 = "INSERT INTO `images` "
            + "(`size`, `content_type`, `thumbnail`, `filename`, `height`, "
            + "`parent_id`, `width`) "
            + "VALUES(12435, 'image/jpeg; charset=ISO-8859-1', 'thumb', ?, 90, ?, 120)";
    private static final String SELECT_DOCUMENTS = "SELECT `documents`.id FROM `documents` "
            + "WHERE (`documents`.`filename` = BINARY ?)  LIMIT 1";
    private static final String INSERT_DOCUMENTS = "INSERT INTO `documents` "
            + "(`size`, `content_type`, `filename`) "
            + "VALUES(129617, 'application/pdf; charset=ISO-8859-1', ?)";
    private static final String INSERT_EVENTS = "INSERT INTO `events` "
            + "(`image_id`, `document_id`, `created_at`, `total_score`, `title`, "
            + "`thumbnail`, `user_id`, `address_id`, `num_votes`, `description`, "
            + "`telephone`, `event_timestamp`, `disabled`, `summary`, `event_date`) "
            + "VALUES(?, ?, ?, NULL, ?, NULL, ?, ?, NULL, ?, ?, ?, NULL, ?, ?)";
    private static final String SELECT_USERS1 = "SELECT * FROM `users` "
            + "WHERE (`users`.`id` = ?)";
    private static final String SELECT_USERS2 = "SELECT `users`.id FROM `users`  "
            + "INNER JOIN `events_users` ON `users`.id = `events_users`.user_id "
            + "WHERE (`users`.`id` = ?) AND (`events_users`.event_id = ? )  LIMIT 1";
    private static final String INSERT_EVENTS_USERS = "INSERT INTO `events_users` "
            + "(`event_id`, `user_id`) VALUES (?, ?)";
    private static final String SELECT_TAGGINGS = "SELECT * FROM `taggings` "
            + "WHERE (`taggings`.taggable_id = ? AND `taggings`.taggable_type = 'Event')";
    private static final String SELECT_TAGS = "SELECT * FROM `tags` "
            + "WHERE (`tags`.`name` = ?)  LIMIT 1";
    private static final String INSERT_TAGS = "INSERT INTO `taggings` "
            + "(`tag_id`, `taggable_id`, `taggable_type`) VALUES(?, ?, 'Event')";
    // Statements
    private PreparedStatement insertAddressesStmt = null;
    private PreparedStatement selectImages1Stmt = null;
    private PreparedStatement insertImages1Stmt = null;
    private PreparedStatement selectImages2Stmt = null;
    private PreparedStatement selectImages3Stmt = null;
    private PreparedStatement insertImages2Stmt = null;
    private PreparedStatement selectDocumentsStmt = null;
    private PreparedStatement insertDocumentsStmt = null;
    private PreparedStatement insertEventsStmt = null;
    private PreparedStatement selectUsers1Stmt = null;
    private PreparedStatement selectUsers2Stmt = null;
    private PreparedStatement insertEventsUsersStmt = null;
    private PreparedStatement selectTaggingsStmt = null;
    private PreparedStatement selectTagsStmt = null;
    private PreparedStatement insertTagsStmt = null;
    // Input
    private Connection conn = null;
    private String[] parameters = null;
    private String[] addressArr = null;
    private Integer threadId = -1;
    private Integer userId = 0;
    // Output
    private MESSAGE message = null;

    public AddEvent(DBConnectionFactory dbConn, String[] parameters,
            String[] addressArr, Integer threadId, Integer userId) {
        this.conn = dbConn.createConnection();
        this.parameters = parameters;
        this.addressArr = addressArr;
        this.threadId = threadId;
        this.userId = userId;
    }

    public void prepare() {
        try {
            insertAddressesStmt = conn.prepareStatement(INSERT_ADDRESSES, Statement.RETURN_GENERATED_KEYS);
            selectImages1Stmt = conn.prepareStatement(SELECT_IMAGES1);
            insertImages1Stmt = conn.prepareStatement(INSERT_IMAGES1, Statement.RETURN_GENERATED_KEYS);
            selectImages2Stmt = conn.prepareStatement(SELECT_IMAGES2);
            selectImages3Stmt = conn.prepareStatement(SELECT_IMAGES3);
            insertImages2Stmt = conn.prepareStatement(INSERT_IMAGES2, Statement.RETURN_GENERATED_KEYS);
            selectDocumentsStmt = conn.prepareStatement(SELECT_DOCUMENTS);
            insertDocumentsStmt = conn.prepareStatement(INSERT_DOCUMENTS, Statement.RETURN_GENERATED_KEYS);
            insertEventsStmt = conn.prepareStatement(INSERT_EVENTS, Statement.RETURN_GENERATED_KEYS);
            selectUsers1Stmt = conn.prepareStatement(SELECT_USERS1);
            selectUsers2Stmt = conn.prepareStatement(SELECT_USERS2);
            insertEventsUsersStmt = conn.prepareStatement(INSERT_EVENTS_USERS, Statement.RETURN_GENERATED_KEYS);
            selectTaggingsStmt = conn.prepareStatement(SELECT_TAGGINGS);
            selectTagsStmt = conn.prepareStatement(SELECT_TAGS);
            insertTagsStmt = conn.prepareStatement(INSERT_TAGS, Statement.RETURN_GENERATED_KEYS);
        } catch (SQLException ex) {
            Logger.getLogger(AddEvent.class.getName()).log(Level.SEVERE, null, ex.getMessage());
        }
    }

    public void execute() {
        prepare();
        Random generator = new Random(System.currentTimeMillis());
        String imagePrefix = String.valueOf(generator.nextInt(1000000000));
        String documentPrefix = String.valueOf(generator.nextInt(1000000000));
        List<String> tagList = java.util.Arrays.asList(parameters[10].split(" "));
        Integer timeOffset = generator.nextInt(630720000);
        try {
            insertAddressesStmt.setString(1, addressArr[2]);
            insertAddressesStmt.setString(2, addressArr[4]);
            insertAddressesStmt.setBigDecimal(3, new java.math.BigDecimal(33.0));
            insertAddressesStmt.setString(4, addressArr[5]);
            insertAddressesStmt.setString(5, addressArr[0]);
            insertAddressesStmt.setString(6, addressArr[1]);
            insertAddressesStmt.setBigDecimal(7, new java.math.BigDecimal(-177.0));
            insertAddressesStmt.setString(8, addressArr[3]);
            insertAddressesStmt.executeUpdate();
            ResultSet insertAddressesResultSet = insertAddressesStmt.getGeneratedKeys();
            int addrIdx = -1;
            if (insertAddressesResultSet.next()) {
                addrIdx = insertAddressesResultSet.getInt(1);
            }

            selectImages1Stmt.setString(1, imagePrefix + threadId + "event.jpg");
            selectImages1Stmt.executeQuery();

            insertImages1Stmt.setString(1, imagePrefix + threadId + "event.jpg");
            insertImages1Stmt.executeUpdate();
            ResultSet insertImages1ResultSet = insertImages1Stmt.getGeneratedKeys();
            int img1Idx = -1;
            if (insertImages1ResultSet.next()) {
                img1Idx = insertImages1ResultSet.getInt(1);
            }

            selectImages2Stmt.setInt(1, img1Idx);
            selectImages2Stmt.executeQuery();

            selectImages3Stmt.setString(1, imagePrefix + threadId + "eventt.jpg");
            selectImages3Stmt.executeQuery();

            insertImages2Stmt.setString(1, imagePrefix + threadId + "eventt.jpg");
            insertImages2Stmt.setInt(2, img1Idx);
            insertImages2Stmt.executeUpdate();

            selectDocumentsStmt.setString(1, documentPrefix + threadId + "event.pdf");
            selectDocumentsStmt.executeQuery();

            insertDocumentsStmt.setString(1, documentPrefix + threadId + "event.pdf");
            insertDocumentsStmt.executeUpdate();
            ResultSet insertDocumentsResultSet = insertDocumentsStmt.getGeneratedKeys();
            int docIdx = -1;
            if (insertDocumentsResultSet.next()) {
                docIdx = insertDocumentsResultSet.getInt(1);
            }

            insertEventsStmt.setInt(1, img1Idx);
            insertEventsStmt.setInt(2, docIdx);
            insertEventsStmt.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));
            insertEventsStmt.setString(4, parameters[0]);
            insertEventsStmt.setInt(5, userId);
            insertEventsStmt.setInt(6, addrIdx);
            insertEventsStmt.setString(7, parameters[2]);
            insertEventsStmt.setString(8, parameters[3]);
            insertEventsStmt.setTimestamp(9, new java.sql.Timestamp(System.currentTimeMillis() + timeOffset));
            insertEventsStmt.setString(10, parameters[1]);
            insertEventsStmt.setDate(11, new java.sql.Date(System.currentTimeMillis() + timeOffset));
            insertEventsStmt.executeUpdate();
            ResultSet insertEventsResultSet = insertEventsStmt.getGeneratedKeys();
            int evnIdx = -1;
            if (insertEventsResultSet.next()) {
                evnIdx = insertEventsResultSet.getInt(1);
            }

            selectUsers1Stmt.setInt(1, userId);
            selectUsers1Stmt.executeQuery();

            boolean usersEventExisted = false;
            selectUsers2Stmt.setInt(1, userId);
            selectUsers2Stmt.setInt(2, evnIdx);
            ResultSet selectUsers2ResultSet = selectUsers2Stmt.executeQuery();
            if (selectUsers2ResultSet.next()) {
                usersEventExisted = true;
            }

            if (!usersEventExisted) {
                insertEventsUsersStmt.setInt(1, evnIdx);
                insertEventsUsersStmt.setInt(2, userId);
                insertEventsUsersStmt.executeUpdate();

                selectTaggingsStmt.setInt(1, evnIdx);
                selectTaggingsStmt.executeQuery();

                int tagIdx = -1;
                for (String tag : tagList) {
                    selectTagsStmt.setString(1, tag);
                    ResultSet selectTagsResultSet = selectTagsStmt.executeQuery();
                    if (selectTagsResultSet.next()) {
                        tagIdx = selectTagsResultSet.getInt("id");
                    }

                    insertTagsStmt.setInt(1, tagIdx);
                    insertTagsStmt.setInt(2, evnIdx);
                    insertTagsStmt.executeUpdate();
                }

                conn.commit();
                message = MESSAGE.COMMITTED;
            }
        } catch (SQLException ex) {
            Logger.getLogger(AddEvent.class.getName()).log(Level.SEVERE, null, ex.getMessage());
            try {
                conn.rollback();
                message = MESSAGE.ROLLBACKED;
            } catch (SQLException ex1) {
                Logger.getLogger(AddEvent.class.getName()).log(Level.SEVERE, null, ex1.getMessage());
            }
        }
        cleanup();
    }

    public MESSAGE getSuccess() {
        return message;
    }

    public void cleanup() {
        try {
            if (!insertAddressesStmt.isClosed()) {
                insertAddressesStmt.close();
            }
            if (!selectImages1Stmt.isClosed()) {
                selectImages1Stmt.close();
            }
            if (!insertImages1Stmt.isClosed()) {
                insertImages1Stmt.close();
            }
            if (!selectImages2Stmt.isClosed()) {
                selectImages2Stmt.close();
            }
            if (!selectImages3Stmt.isClosed()) {
                selectImages3Stmt.close();
            }
            if (!insertImages2Stmt.isClosed()) {
                insertImages2Stmt.close();
            }
            if (!selectDocumentsStmt.isClosed()) {
                selectDocumentsStmt.close();
            }
            if (!insertDocumentsStmt.isClosed()) {
                insertDocumentsStmt.close();
            }
            if (!insertEventsStmt.isClosed()) {
                insertEventsStmt.close();
            }
            if (!selectUsers1Stmt.isClosed()) {
                selectUsers1Stmt.close();
            }
            if (!selectUsers2Stmt.isClosed()) {
                selectUsers2Stmt.close();
            }
            if (!insertEventsUsersStmt.isClosed()) {
                insertEventsUsersStmt.close();
            }
            if (!selectTaggingsStmt.isClosed()) {
                selectTaggingsStmt.close();
            }
            if (!selectTagsStmt.isClosed()) {
                selectTagsStmt.close();
            }
            if (!insertTagsStmt.isClosed()) {
                insertTagsStmt.close();
            }
        } catch (SQLException ex) {
            Logger.getLogger(TagSearch.class.getName()).log(Level.SEVERE, null, ex.getMessage());
        }
    }
}
