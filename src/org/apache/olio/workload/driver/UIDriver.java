/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.olio.workload.driver;

import com.sun.faban.driver.*;
import org.apache.olio.workload.util.RandomUtil;
import org.apache.olio.workload.util.ScaleFactors;
import org.apache.olio.workload.util.UserName;
import org.apache.olio.workload.driver.common.*;
import org.apache.olio.workload.driver.operations.*;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.xml.xpath.XPathExpressionException;

@BenchmarkDefinition(name = "OlioDatabase",
version = "0.3",
scaleName = "Concurrent Users")
@BenchmarkDriver(name = "UIDriver",
threadPerScale = 1)
// 90/10 Read/Write ratio
@MatrixMix( //    operations = {"HomePage", "Login", , "TagSearch", "EventDetail", "PersonDetail",
//                  "Logout", "AddEvent",  "AddPerson"},
operations = {"HomePage", "Login", "TagSearch", "EventDetail", "PersonDetail", "AddPerson", "AddEvent"},
mix = {
    @Row({0, 11, 52, 36, 0, 1, 0}), // Home Page
    @Row({0, 0, 60, 20, 0, 0, 20}), // Login
    @Row({21, 6, 41, 31, 0, 1, 0}), // Tag Search
    @Row({72, 21, 0, 0, 6, 1, 0}), // Event Detail
    @Row({52, 6, 0, 31, 11, 0, 0}), // Person Detail
    @Row({0, 0, 0, 0, 100, 0, 0}), // Add Person
    @Row({0, 0, 0, 100, 0, 0, 0}) // Add Event
})
@NegativeExponential(cycleType = CycleType.CYCLETIME,
cycleMean = 5000,
cycleDeviation = 2)
public class UIDriver {

    private DriverContext ctx;
    private boolean isLoggedOn = false;
    private String username = null;
    private int randomId = 0;
    private com.sun.faban.driver.util.Random random;
    private DateFormat df;
    private String selectedEvent;
    private int personsAdded = 0;
    private int loadedUsers;
    private HashSet<String> cachedURLs = new HashSet<String>();
    private StringBuilder tags = new StringBuilder();
    private LinkedHashSet<Integer> tagSet = new LinkedHashSet<Integer>(7);
    private DBReadConnectionFactory dbReadConn;
    private DBWriteConnectionFactory dbWriteConn;

    public UIDriver() throws XPathExpressionException {
        ctx = DriverContext.getContext();
        int scale = ctx.getScale();
        ScaleFactors.setActiveUsers(scale);

        random = ctx.getRandom();

        String[] dbhosts = ctx.getXPathValue(
                "/olio/dbServer/fa:hostConfig/fa:host").split(" ");
        String dbhost_list = new String();

        int loadedScale = Integer.parseInt(
                ctx.getXPathValue("/olio/dbServer/scale"));
        loadedUsers = ScaleFactors.USERS_RATIO * loadedScale;
        if (scale > loadedScale) {
            throw new FatalException("Data loaded only for " + loadedScale
                    + " concurrent users. Run is set for " + scale
                    + " concurrent users. Please load for enough concurrent "
                    + "users. Run terminating!");
        }


        for (int i = 0; i < dbhosts.length - 1; i++) {
            dbhost_list += dbhosts[i] + ",";
        }
        dbhost_list += dbhosts[dbhosts.length - 1];


        dbWriteConn = new DBWriteConnectionFactory(dbhost_list);
        dbReadConn = new DBReadConnectionFactory(dbhost_list);

        isLoggedOn = false;
    }

    @BenchmarkOperation(name = "HomePage",
    max90th = 1,
    timing = Timing.MANUAL)
    public void doHomePage() throws IOException {
        HomePage homePage = new HomePage(dbReadConn);
        ctx.recordTime();
        homePage.execute();
        ctx.pauseTime();

        selectedEvent = RandomUtil.randomEvent(random, homePage.getEventIds());
    }

    @BenchmarkOperation(name = "Login",
    max90th = 1,
    timing = Timing.MANUAL)
    public void doLogin() throws IOException, Exception {
        if (isLoggedOn) {
            doLogout();
        }

        randomId = selectUserID();
        username = UserName.getUserName(randomId);
        Login login = new Login(dbReadConn, username, randomId);
        ctx.recordTime();
        login.execute();
        ctx.pauseTime();

        int loginIdx = login.getLoginIdx();
        if (loginIdx != -1) {
            throw new Exception(" Found login prompt at index " + loginIdx);
        }

        isLoggedOn = true;
    }

    @BenchmarkOperation(name = "Logout",
    max90th = 1,
    timing = Timing.MANUAL)
    public void doLogout() throws IOException {
        if (isLoggedOn) {
            isLoggedOn = false;
            username = null;
            randomId = 0;
        }
    }

    @BenchmarkOperation(name = "TagSearch",
    max90th = 2,
    timing = Timing.MANUAL)
    public void doTagSearch() throws IOException {
        String tag = RandomUtil.randomTagName(random);

        TagSearch tagSearch = new TagSearch(dbReadConn, tag);
        ctx.recordTime();
        tagSearch.execute();
        ctx.pauseTime();

        String event = RandomUtil.randomEvent(random, tagSearch.getEventIds());
        if (event != null) {
            selectedEvent = event;
        }
    }

    @BenchmarkOperation(name = "AddEvent",
    max90th = 4,
    timing = Timing.MANUAL)
    @NegativeExponential(cycleType = CycleType.CYCLETIME,
    cycleMean = 5000,
    cycleMin = 3000,
    truncateAtMin = false,
    cycleDeviation = 2)
    public void doAddEvent() throws Exception {
        if (!isLoggedOn) {
            throw new IOException("User not logged when trying to add an event");
        }

        String[] addressArr = prepareAddress();
        String[] parameters = prepareEvent();
        if (parameters[0] == null || parameters[0].length() == 0) {
        } else {
            AddEvent addEvent = new AddEvent(dbWriteConn, parameters, addressArr,
                    ctx.getThreadId(), randomId);
            ctx.recordTime();
            addEvent.execute();
            ctx.pauseTime();

            String message = addEvent.getSuccess();
            if (message != null) {
                throw new Exception(message);
            }
        }
    }

    @BenchmarkOperation(name = "AddPerson",
    max90th = 3,
    timing = Timing.MANUAL)
    @NegativeExponential(cycleType = CycleType.CYCLETIME,
    cycleMean = 5000,
    cycleMin = 2000,
    truncateAtMin = false,
    cycleDeviation = 2)
    public void doAddPerson() throws Exception {
        if (isLoggedOn) {
            doLogout();
        }

        String[] parameters = preparePerson();
        String[] addressArr = prepareAddress();
        // Debug
        if (parameters[0] == null || parameters[0].length() == 0) {
        } else {
            AddPerson addPerson = new AddPerson(dbWriteConn, parameters,
                    addressArr, ctx.getThreadId());
            ctx.recordTime();
            addPerson.execute();
            ctx.pauseTime();

            String message = addPerson.getSuccess();
            if (message != null) {
                throw new Exception(message);
            }
        }
    }

    @BenchmarkOperation(name = "EventDetail",
    max90th = 2,
    timing = Timing.MANUAL)
    public void doEventDetail() throws Exception {
        //select random event
        if (selectedEvent == null) {
            HomePage homePage = new HomePage(dbReadConn);
            ctx.recordTime();
            homePage.execute();
            ctx.pauseTime();
            selectedEvent = RandomUtil.randomEvent(random, homePage.getEventIds());
            if (selectedEvent == null) {
                throw new IOException("In event detail and select event is null");
            }
        }

        EventDetail eventDetail = new EventDetail(dbReadConn, selectedEvent);
        ctx.recordTime();
        eventDetail.execute();
        ctx.pauseTime();

        boolean canAddAttendee = isLoggedOn && 
                !(eventDetail.getAttendeeList().contains(randomId));
        if (canAddAttendee) {
            // 10% of the time we can add ourselves, we will.
            int card = random.random(0, 9);
            if (card == 0) {
                AddAttendee addAttendee = new AddAttendee(dbWriteConn, selectedEvent, randomId);
                ctx.recordTime();
                addAttendee.execute();
                ctx.pauseTime();
            }
        }
    }

    @BenchmarkOperation(name = "PersonDetail",
    max90th = 2,
    timing = Timing.MANUAL)
    public void doPersonDetail() throws IOException {
        int id = random.random(1, ScaleFactors.users);
        PersonDetail personDetail = new PersonDetail(dbReadConn, id);
        ctx.recordTime();
        personDetail.execute();
        ctx.pauseTime();

        String event = RandomUtil.randomEvent(random, personDetail.getEventIds());
        if (event != null) {
            selectedEvent = event;
        }
    }

    public DateFormat getDateFormat() {
        if (df == null) {
            df = new SimpleDateFormat("yyyy-MM-dd-hh-mm");
        }
        return df;
    }

    public int selectUserID() {
        return random.random(0, ScaleFactors.USERS_RATIO - 1)
                * ScaleFactors.activeUsers + ctx.getThreadId() + 1;
    }

    public String[] prepareEvent() {

        String fields[] = new String[11];
        StringBuilder buffer = new StringBuilder(256);
        fields[0] = RandomUtil.randomText(random, 15, 20); //title
        fields[1] = RandomUtil.randomText(random, 50, 200); //summary
        fields[2] = RandomUtil.randomText(random, 100, 495); // description

        int numTags = random.random(1, 7); // Avg is 4 tags per event
        for (int i = 0; i < numTags; i++) {
            tagSet.add(RandomUtil.randomTagId(random, 0.1d));
        }

        for (int tagId : tagSet) {
            tags.append(UserName.getUserName(tagId)).append(' ');
        }
        tags.setLength(tags.length() - 1);

        fields[10] = tags.toString();
        tags.setLength(0);
        tagSet.clear();

        fields[3] = RandomUtil.randomPhone(random, buffer); //phone
        fields[4] = RandomUtil.randomTimeZone(random); // timezone
        DateFormat dateFormat = getDateFormat(); // eventtimestamp
        String dateTime = dateFormat.format( //eventtimestamp
                random.makeDateInInterval(new java.sql.Date(System.currentTimeMillis()), 0, 540));
        StringTokenizer t = new StringTokenizer(dateTime, "-");
        int i = 5;
        while (t.hasMoreTokens()) {
            fields[i++] = t.nextToken();
        }
        return fields;
    }

    public String[] prepareAddress() {

        String[] STREETEXTS = {"Blvd", "Ave", "St", "Ln", ""};
        StringBuilder buffer = new StringBuilder(255);
        buffer.append(random.makeNString(1, 5)).append(' '); // number
        RandomUtil.randomName(random, buffer, 1, 11); // street
        String streetExt = STREETEXTS[random.random(0, STREETEXTS.length - 1)];
        if (streetExt.length() > 0) {
            buffer.append(' ').append(streetExt);
        }
        String[] fields = new String[6];
        fields[0] = buffer.toString();

        int toggle = random.random(0, 1); // street2
        if (toggle > 0) {
            fields[1] = random.makeCString(5, 20);
        } else {
            fields[1] = "";
        }

        fields[2] = random.makeCString(4, 14); // city
        fields[3] = random.makeCString(2, 2).toUpperCase(); // state
        fields[4] = random.makeNString(5, 5);  // zip

        toggle = random.random(0, 1);
        if (toggle == 0) {
            fields[5] = "USA";
        } else {
            buffer.setLength(0);
            fields[5] = RandomUtil.randomName(random, buffer, 6, 16).toString();
        }
        return fields;
    }

    public String[] preparePerson() {
        String fields[] = new String[8];
        StringBuilder b = new StringBuilder(256);
        int id = loadedUsers + personsAdded++ * ScaleFactors.activeUsers
                + ctx.getThreadId() + 1;
        fields[0] = UserName.getUserName(id);
        //use the same field for repeating the password field.
        fields[1] = String.valueOf(id);
        fields[2] = RandomUtil.randomName(random, b, 2, 12).toString();
        b.setLength(0);
        fields[3] = RandomUtil.randomName(random, b, 5, 15).toString();
        fields[4] = random.makeCString(3, 10);
        fields[4] = fields[2] + '_' + fields[3] + '@' + fields[4] + ".com";
        b.setLength(0);
        fields[5] = RandomUtil.randomPhone(random, b);
        fields[6] = random.makeAString(250, 2500);
        fields[7] = RandomUtil.randomTimeZone(random);
        return fields;
    }
}
