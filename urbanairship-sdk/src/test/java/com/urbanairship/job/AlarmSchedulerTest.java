/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.urbanairship.AirshipService;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

public class AlarmSchedulerTest extends BaseTestCase {

    private AlarmScheduler scheduler;
    private Context context;

    @Before
    public void setup() {
        scheduler = new AlarmScheduler();
        context = TestApplication.getApplication();
    }

    @Test
    public void testRequiresScheduling() {
        JobInfo jobInfoWithDelay = JobInfo.newBuilder().setInitialDelay(1, TimeUnit.MILLISECONDS).build();
        assertTrue(scheduler.requiresScheduling(context, jobInfoWithDelay));

        JobInfo jobInfo = JobInfo.newBuilder().build();
        assertFalse(scheduler.requiresScheduling(context, jobInfo));
    }

    @Test
    public void testSchedule() throws SchedulerException {
        JobInfo jobInfo = JobInfo.newBuilder().setInitialDelay(1, TimeUnit.MILLISECONDS).build();

        scheduler.schedule(context, jobInfo);
        verifyScheduledJob(jobInfo, 1);
    }

    @Test
    public void testScheduleWithTag() throws SchedulerException {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setInitialDelay(1, TimeUnit.MILLISECONDS)
                                 .setTag("tag")
                                 .build();

        scheduler.schedule(context, jobInfo);
        verifyScheduledJob(jobInfo, 1);
    }

    @Test
    public void testReschedule() throws SchedulerException {
        JobInfo jobInfo = JobInfo.newBuilder().setInitialDelay(1, TimeUnit.MILLISECONDS).build();

        // Check 10 retries. The delay should double each time
        long delay = 10000;
        for (int i = 0; i < 10; i++) {
            scheduler.reschedule(context, jobInfo);
            verifyScheduledJob(jobInfo, delay);
            delay = delay * 2;
        }
    }

    private void verifyScheduledJob(JobInfo jobInfo, long delay) {
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ShadowAlarmManager.ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();
        assertNotNull(alarm);

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(alarm.operation);
        assertTrue(shadowPendingIntent.isServiceIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals(new ComponentName(context, AirshipService.class), intent.getComponent());
        assertEquals(AirshipService.ACTION_RUN_JOB, intent.getAction());
        assertBundlesEquals(jobInfo.toBundle(), intent.getBundleExtra(AirshipService.EXTRA_JOB_BUNDLE));
        assertEquals(jobInfo.getTag(), intent.getCategories().iterator().next());

        long expectedTriggerTime = SystemClock.elapsedRealtime() + delay;
        // Verify the alarm is within 100 milliseconds
        assertTrue(expectedTriggerTime - alarm.triggerAtTime <= 100);
    }
}