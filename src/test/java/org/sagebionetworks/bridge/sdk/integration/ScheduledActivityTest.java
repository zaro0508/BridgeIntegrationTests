package org.sagebionetworks.bridge.sdk.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.sdk.integration.Tests.assertDatesWithTimeZoneEqual;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

import org.sagebionetworks.bridge.rest.RestUtils;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.api.SchedulesApi;
import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.ActivityType;
import org.sagebionetworks.bridge.rest.model.ForwardCursorScheduledActivityList;
import org.sagebionetworks.bridge.rest.model.Role;
import org.sagebionetworks.bridge.rest.model.Schedule;
import org.sagebionetworks.bridge.rest.model.SchedulePlan;
import org.sagebionetworks.bridge.rest.model.ScheduleStatus;
import org.sagebionetworks.bridge.rest.model.ScheduleType;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityList;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityListV4;
import org.sagebionetworks.bridge.rest.model.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.rest.model.TaskReference;
import org.sagebionetworks.bridge.user.TestUserHelper;
import org.sagebionetworks.bridge.user.TestUserHelper.TestUser;

@Category(IntegrationSmokeTest.class)
@SuppressWarnings("ConstantConditions")
public class ScheduledActivityTest {
    
    private static final DateTimeZone EST = DateTimeZone.forOffsetHours(-5);
    
    public static class ClientData {
        final String name;
        final boolean enabled;
        final int count;
        public ClientData(String name, boolean enabled, int count) {
            this.name = name;
            this.enabled = enabled;
            this.count = count;
        }
        public String getName() {
            return name;
        }
        public boolean getEnabled() {
            return enabled;
        }
        public int getCount() {
            return count;
        }
    }

    private String runId;
    private String monthlyActivityLabel;
    private String oneTimeActivityLabel;
    private TestUser researcher;
    private TestUser user;
    private TestUser developer;
    private SchedulesApi schedulePlansApi;
    private List<String> schedulePlanGuidList;
    private ForConsentedUsersApi usersApi;

    @Before
    public void before() throws Exception {
        schedulePlanGuidList = new ArrayList<>();

        researcher = TestUserHelper.createAndSignInUser(ScheduledActivityTest.class, true, Role.RESEARCHER);
        developer = TestUserHelper.createAndSignInUser(ScheduledActivityTest.class, true, Role.DEVELOPER);
        user = TestUserHelper.createAndSignInUser(ScheduledActivityTest.class, true);

        schedulePlansApi = developer.getClient(SchedulesApi.class);
        usersApi = user.getClient(ForConsentedUsersApi.class);

        // Run ID is a random string, used to uniquely identify schedules for this test.
        runId = RandomStringUtils.randomAlphabetic(4);
    }

    private void monthlyAfterOneMonthSchedule() throws IOException {
        monthlyActivityLabel = "monthly-activity-" + runId;

        String planGuid;
        Schedule schedule = new Schedule();
        schedule.setLabel(monthlyActivityLabel);
        schedule.setDelay("P1M");
        schedule.setInterval("P1M");
        schedule.setExpires("P3W");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setTimes(Lists.newArrayList("10:00"));
        
        TaskReference taskReference = new TaskReference();
        taskReference.setIdentifier("task:BBB");
        
        Activity activity = new Activity();
        activity.setLabel(monthlyActivityLabel);
        activity.setTask(taskReference);
        schedule.setActivities(Lists.newArrayList(activity));
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        strategy.setType("SimpleScheduleStrategy");
        
        SchedulePlan plan = new SchedulePlan();
        plan.setLabel("Schedule plan 2");
        plan.setStrategy(strategy);
        planGuid = schedulePlansApi.createSchedulePlan(plan).execute().body().getGuid();
        schedulePlanGuidList.add(planGuid);
    }

    private void oneTimeScheduleAfter3Days() throws IOException {
        oneTimeActivityLabel = "one-time-activity-" + runId;

        Schedule schedule = new Schedule();
        schedule.setLabel(oneTimeActivityLabel);
        schedule.setDelay("P3D");
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setTimes(Lists.newArrayList("10:00"));
        
        TaskReference taskReference = new TaskReference();
        taskReference.setIdentifier("task:AAA");
        
        Activity activity = new Activity();
        activity.setLabel(oneTimeActivityLabel);
        activity.setTask(taskReference);
        
        schedule.setActivities(Lists.newArrayList(activity));

        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        strategy.setType("SimpleScheduleStrategy");
        
        SchedulePlan plan = new SchedulePlan();
        plan.setLabel("Schedule plan 1");
        plan.setStrategy(strategy);
        String planGuid = schedulePlansApi.createSchedulePlan(plan).execute().body().getGuid();
        schedulePlanGuidList.add(planGuid);
    }
    
    private void dailyTaskAt4Times() throws IOException {
        Schedule schedule = new Schedule();
        schedule.setLabel("Daily Task at 4 times");
        schedule.setExpires("P1D");
        schedule.setInterval("P1D");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setTimes(Lists.newArrayList("06:00", "10:00", "14:00", "18:00"));
        
        TaskReference taskReference = new TaskReference();
        taskReference.setIdentifier("task:AAA");
        
        Activity activity = new Activity();
        activity.setLabel("task:AAA activity");
        activity.setTask(taskReference);
        
        schedule.setActivities(Lists.newArrayList(activity));

        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        strategy.setType("SimpleScheduleStrategy");
        
        SchedulePlan plan = new SchedulePlan();
        plan.setLabel("Daily task schedule plan");
        plan.setStrategy(strategy);
        String planGuid = schedulePlansApi.createSchedulePlan(plan).execute().body().getGuid();
        schedulePlanGuidList.add(planGuid);
    }

    private void miniStudyBurstSchedule() throws Exception {
        Schedule schedule = new Schedule();
        schedule.setLabel("Mini Study Burst");
        schedule.setEventId("two_weeks_before_enrollment,enrollment");
        schedule.setExpires("P1M");
        schedule.setInterval("P1D");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setSequencePeriod("P3D");
        schedule.setTimes(ImmutableList.of("06:00"));

        TaskReference taskReference = new TaskReference();
        taskReference.setIdentifier("task:AAA");

        Activity activity = new Activity();
        activity.setLabel("mini-study-burst-" + runId);
        activity.setTask(taskReference);

        schedule.setActivities(ImmutableList.of(activity));

        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        strategy.setType("SimpleScheduleStrategy");

        SchedulePlan plan = new SchedulePlan();
        plan.setLabel("Mini Study Burst");
        plan.setStrategy(strategy);
        String planGuid = schedulePlansApi.createSchedulePlan(plan).execute().body().getGuid();
        schedulePlanGuidList.add(planGuid);
    }

    @After
    public void after() throws Exception {
        try {
            for (String oneSchedulePlanGuid : schedulePlanGuidList) {
                schedulePlansApi.deleteSchedulePlan(oneSchedulePlanGuid).execute();
            }
        } finally {
            if (researcher != null) {
                researcher.signOutAndDeleteUser();
            }
            if (developer != null) {
                developer.signOutAndDeleteUser();
            }
            if (user != null) {
                user.signOutAndDeleteUser();
            }
        }
    }
    
    @Test
    public void getScheduledActivityHistoryV4() throws InterruptedException, IOException {
        dailyTaskAt4Times();
        
        DateTime startTime = DateTime.now(EST);
        DateTime endTime = DateTime.now(EST).plusDays(4);
        
        usersApi.getScheduledActivitiesByDateRange(startTime, endTime).execute();

        // getTaskHistory() uses a secondary global index. Sleep for 2 seconds to help make sure the index is consistent.
        Thread.sleep(2000);

        // Now we should see those in the latest API:
        ForwardCursorScheduledActivityList list = usersApi.getTaskHistory("task:AAA", startTime, endTime, null, 10)
                .execute().body();
        
        // Joda DateTime equality is only object instance equality, use strings to compare
        assertNotNull(list.getNextPageOffsetKey()); 
        assertEquals(startTime.toString(), list.getRequestParams().getScheduledOnStart().toString());
        assertEquals(endTime.toString(), list.getRequestParams().getScheduledOnEnd().toString());
        assertEquals(10, list.getRequestParams().getPageSize().intValue());
        assertNull(list.getRequestParams().getOffsetKey());
        
        Set<String> guids = Sets.newHashSet();
        
        int pageOneCount = list.getItems().size();
        list.getItems().stream().map(ScheduledActivity::getGuid).forEach(guids::add);
        
        list = usersApi.getTaskHistory("task:AAA", startTime, endTime, list.getNextPageOffsetKey(), 10).execute()
                .body();
        int pageTwoCount = list.getItems().size();
        list.getItems().stream().map(ScheduledActivity::getGuid).forEach(guids::add);
        
        assertEquals(pageOneCount+pageTwoCount, guids.size());
        assertNull(list.getNextPageOffsetKey());
    }
    
    @Test
    public void createSchedulePlanGetScheduledActivities() throws Exception {
        oneTimeScheduleAfter3Days();
        monthlyAfterOneMonthSchedule();
        
        // Get scheduled activities. Validate basic properties.
        ScheduledActivityList scheduledActivities = usersApi.getScheduledActivities("-05:00", 4, 0).execute().body();
        ScheduledActivity schActivity = findOneTimeActivity(scheduledActivities.getItems());
        assertNotNull(schActivity);
        assertEquals(ScheduleStatus.SCHEDULED, schActivity.getStatus());
        assertNotNull(schActivity.getScheduledOn());
        assertNull(schActivity.getExpiresOn());

        Activity activity = schActivity.getActivity();
        assertEquals(ActivityType.TASK, activity.getActivityType());
        assertEquals(oneTimeActivityLabel, activity.getLabel());
        assertEquals("task:AAA", activity.getTask().getIdentifier());

        DateTime startDateTime = DateTime.now(EST).minusDays(10);
        DateTime endDateTime = DateTime.now(EST).plusDays(10);

        // You can see this activity in history...
        ForwardCursorScheduledActivityList list = usersApi.getActivityHistory(activity.getGuid(),
                startDateTime, endDateTime, null, 5).execute().body();

        ScheduledActivity retrievedFromHistory = list.getItems().get(0);
        Tests.setVariableValueInObject(retrievedFromHistory, "schedulePlanGuid", schActivity.getSchedulePlanGuid());
        assertEquals(schActivity, retrievedFromHistory);
        
        // You can see this activity in the researcher API
        ParticipantsApi participantsApi = researcher.getClient(ParticipantsApi.class);
        list = participantsApi.getParticipantActivityHistory(user.getSession().getId(), activity.getGuid(),
                startDateTime, endDateTime, null, 5).execute().body();
        
        retrievedFromHistory = list.getItems().get(0);
        Tests.setVariableValueInObject(retrievedFromHistory, "schedulePlanGuid", schActivity.getSchedulePlanGuid());
        assertEquals(schActivity, retrievedFromHistory);
        assertFalse(list.getHasNext());
        assertEquals(startDateTime, list.getRequestParams().getScheduledOnStart());
        assertEquals(endDateTime, list.getRequestParams().getScheduledOnEnd());
        
        // If we ask for a date range that doesn't include it, it is not returned.
        list = participantsApi.getParticipantActivityHistory(user.getSession().getId(), activity.getGuid(),
                DateTime.now(EST).plusDays(10), DateTime.now(EST).plusDays(12), null, 5).execute().body();
        assertTrue(list.getItems().isEmpty());
        
        // Start the activity.
        schActivity.setStartedOn(DateTime.now());
        schActivity.setClientData(new ClientData("Test Name", true, 100));
        usersApi.updateScheduledActivities(scheduledActivities.getItems()).execute();

        // Get activities back and validate that it's started.
        scheduledActivities = usersApi.getScheduledActivities("+00:00", 3, null).execute().body();
        schActivity = findOneTimeActivity(scheduledActivities.getItems());
        assertNotNull(schActivity);
        ClientData clientData = RestUtils.toType(schActivity.getClientData(), ClientData.class);
        assertEquals("Test Name", clientData.getName());
        assertTrue(clientData.getEnabled());
        assertEquals(100, clientData.getCount());
        assertEquals(ScheduleStatus.STARTED, schActivity.getStatus());

        // Finish the activity, save some data
        schActivity.setFinishedOn(DateTime.now());
        usersApi.updateScheduledActivities(scheduledActivities.getItems()).execute();

        // Get activities back. Verify the activity is not there.
        scheduledActivities = usersApi.getScheduledActivities("+00:00", 3, null).execute().body();
        schActivity = findOneTimeActivity(scheduledActivities.getItems());
        assertNull(schActivity);
        
        // But the activities continue to be in the history APIs
        list = usersApi.getActivityHistory(activity.getGuid(),
                startDateTime, endDateTime, null, 5).execute().body();
        assertFalse(list.getItems().isEmpty());
        assertNotNull(list.getItems().get(0).getFinishedOn());
        
        list = participantsApi.getParticipantActivityHistory(user.getSession().getId(), activity.getGuid(),
                startDateTime, endDateTime, null, 5).execute().body();
        assertFalse(list.getItems().isEmpty());
        assertNotNull(list.getItems().get(0).getFinishedOn());
    }

    @Test
    public void createSchedulePlanGetScheduledActivitiesV4() throws Exception {
        oneTimeScheduleAfter3Days();
        monthlyAfterOneMonthSchedule();
        
        // Get scheduled activities. Validate basic properties.
        DateTime startsOn = DateTime.now(EST);
        DateTime endsOn = startsOn.plusDays(4);
        
        ScheduledActivityListV4 scheduledActivities = usersApi.getScheduledActivitiesByDateRange(startsOn, endsOn).execute().body();
        ScheduledActivity schActivity = findOneTimeActivity(scheduledActivities.getItems());
        assertNotNull(schActivity);
        assertEquals(ScheduleStatus.SCHEDULED, schActivity.getStatus());
        assertNotNull(schActivity.getScheduledOn());
        assertNull(schActivity.getExpiresOn());

        Activity activity = schActivity.getActivity();
        assertEquals(ActivityType.TASK, activity.getActivityType());
        assertEquals(oneTimeActivityLabel, activity.getLabel());
        assertEquals("task:AAA", activity.getTask().getIdentifier());

        // Historical items should come back with the same time zone as get() method, and so activities should
        // be strictly equal.
        DateTime startDateTime = startsOn.minusDays(10);
        DateTime endDateTime = startsOn.plusDays(10);

        // You can see this activity in history...
        ForwardCursorScheduledActivityList list = usersApi.getActivityHistory(activity.getGuid(),
                startDateTime, endDateTime, null, 5).execute().body();

        ScheduledActivity retrievedFromHistory = list.getItems().get(0);
        Tests.setVariableValueInObject(retrievedFromHistory, "schedulePlanGuid", schActivity.getSchedulePlanGuid());
        assertEquals(schActivity, retrievedFromHistory);
        
        // You can see this activity in the researcher API
        ParticipantsApi participantsApi = researcher.getClient(ParticipantsApi.class);
        list = participantsApi.getParticipantActivityHistory(user.getSession().getId(), activity.getGuid(),
                startDateTime, endDateTime, null, 5).execute().body();
        
        retrievedFromHistory = list.getItems().get(0);
        Tests.setVariableValueInObject(retrievedFromHistory, "schedulePlanGuid", schActivity.getSchedulePlanGuid());
        assertEquals(schActivity, retrievedFromHistory);
        assertFalse(list.getHasNext());
        assertEquals(startDateTime, list.getRequestParams().getScheduledOnStart());
        assertEquals(endDateTime, list.getRequestParams().getScheduledOnEnd());
        
        // If we ask for a date range that doesn't include it, it is not returned.
        // This started failing around the time zone shift, we have to fix the time zone or we'll get a server error.
        list = participantsApi.getParticipantActivityHistory(user.getSession().getId(), activity.getGuid(),
                DateTime.now(DateTimeZone.UTC).plusDays(10), DateTime.now(DateTimeZone.UTC).plusDays(12), null, 5)
                .execute().body();
        assertTrue(list.getItems().isEmpty());
        
        // Start the activity.
        schActivity.setStartedOn(DateTime.now());
        schActivity.setClientData(new ClientData("Test Name", true, 100));
        usersApi.updateScheduledActivities(scheduledActivities.getItems()).execute();

        // Get activities back and validate that it's started.
        // It's delayed by three days, so ask for four
        scheduledActivities = usersApi.getScheduledActivitiesByDateRange(startsOn, endsOn).execute().body();
        schActivity = findOneTimeActivity(scheduledActivities.getItems());
        assertNotNull(schActivity);
        ClientData clientData = RestUtils.toType(schActivity.getClientData(), ClientData.class);
        assertEquals("Test Name", clientData.getName());
        assertTrue(clientData.getEnabled());
        assertEquals(100, clientData.getCount());
        assertEquals(ScheduleStatus.STARTED, schActivity.getStatus());

        // Finish the activity, save some data
        schActivity.setFinishedOn(DateTime.now());
        usersApi.updateScheduledActivities(scheduledActivities.getItems()).execute();

        // Get activities back. Verify the activity is there and it is finished
        scheduledActivities = usersApi.getScheduledActivitiesByDateRange(startsOn, endsOn).execute().body();
        schActivity = findOneTimeActivity(scheduledActivities.getItems());
        assertNotNull(schActivity.getStartedOn());
        assertNotNull(schActivity.getFinishedOn());
        
        // The activities also continue to be in the history APIs
        list = usersApi.getActivityHistory(activity.getGuid(), startDateTime, endDateTime, null, 5).execute().body();
        assertFalse(list.getItems().isEmpty());
        assertNotNull(list.getItems().get(0).getFinishedOn());
        
        list = participantsApi.getParticipantActivityHistory(user.getSession().getId(), activity.getGuid(),
                startDateTime, endDateTime, null, 5).execute().body();
        assertFalse(list.getItems().isEmpty());
        assertNotNull(list.getItems().get(0).getFinishedOn());
    }
    
    @Test
    public void getScheduledActivitiesWithMinimumActivityValue() throws Exception {
        oneTimeScheduleAfter3Days();
        monthlyAfterOneMonthSchedule();
        
        ScheduledActivityList scheduledActivities = usersApi.getScheduledActivities("+00:00", 4, 2).execute().body();
        
        Multiset<String> idCounts = getActivityLabels(scheduledActivities);
        assertEquals(1, idCounts.count(oneTimeActivityLabel));
        assertEquals(2, idCounts.count(monthlyActivityLabel));
        
        scheduledActivities = usersApi.getScheduledActivities("+00:00", 4, 0).execute().body();
        idCounts = getActivityLabels(scheduledActivities);
        assertEquals(1, idCounts.count(oneTimeActivityLabel));
        assertEquals(0, idCounts.count(monthlyActivityLabel));
        
        scheduledActivities = usersApi.getScheduledActivities("+00:00", 4, 5).execute().body();
        idCounts = getActivityLabels(scheduledActivities);
        assertEquals(1, idCounts.count(oneTimeActivityLabel));
        assertEquals(5, idCounts.count(monthlyActivityLabel));
    }

    @Test
    public void scheduleWithMultipleEventIds() throws Exception {
        DateTime now = DateTime.now(DateTimeZone.forOffsetHours(-8));

        // Set up schedule
        miniStudyBurstSchedule();

        // Get activities, and filter on activity label.
        List<ScheduledActivity> scheduledActivityList = usersApi.getScheduledActivities("-08:00", 7,
                null).execute().body().getItems();
        List<ScheduledActivity> filteredActivityList = scheduledActivityList.stream()
                .filter(act -> act.getActivity().getLabel().equals("mini-study-burst-" + runId))
                .collect(Collectors.toList());

        // Activities should be generated and returned in order, starting from 6am 2 weeks before enrollment.
        assertEquals(6, filteredActivityList.size());
        assertDatesWithTimeZoneEqual(now.minusDays(14).withTime(6, 0, 0, 0), filteredActivityList.get(0)
                .getScheduledOn());
        assertDatesWithTimeZoneEqual(now.minusDays(13).withTime(6, 0, 0, 0), filteredActivityList.get(1)
                .getScheduledOn());
        assertDatesWithTimeZoneEqual(now.minusDays(12).withTime(6, 0, 0, 0), filteredActivityList.get(2)
                .getScheduledOn());
        assertDatesWithTimeZoneEqual(now.withTime(6, 0, 0, 0), filteredActivityList.get(3)
                .getScheduledOn());
        assertDatesWithTimeZoneEqual(now.plusDays(1).withTime(6, 0, 0, 0), filteredActivityList.get(4)
                .getScheduledOn());
        assertDatesWithTimeZoneEqual(now.plusDays(2).withTime(6, 0, 0, 0), filteredActivityList.get(5)
                .getScheduledOn());
    }

    // api study may have other schedule plans in it with other scheduled activities. To prevent tests from
    // conflicting, look for the activity with oneTimeActivityLabel.
    private ScheduledActivity findOneTimeActivity(List<ScheduledActivity> scheduledActivityList) {
        for (ScheduledActivity oneScheduledActivity : scheduledActivityList) {
            if (oneTimeActivityLabel.equals(oneScheduledActivity.getActivity().getLabel())) {
                return oneScheduledActivity;
            }
        }
        // It doesn't exist. Return null and let the test deal with it.
        return null;
    }
    
    private Multiset<String> getActivityLabels(ScheduledActivityList scheduledActivities) {
        return HashMultiset.create(scheduledActivities.getItems().stream()
                .map((act) -> act.getActivity().getLabel())
                .collect(Collectors.toList()));
    }
}
