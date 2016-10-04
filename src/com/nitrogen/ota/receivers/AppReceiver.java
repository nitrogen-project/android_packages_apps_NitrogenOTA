/*
 * Copyright (C) 2015 Matt Booth (Kryten2k35).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nitrogen.ota.receivers;

import java.util.Iterator;
import java.util.Set;

import android.app.DownloadManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;

import com.nitrogen.ota.NitrogenOTA;
import com.nitrogen.ota.R;
import com.nitrogen.ota.RomUpdate;
import com.nitrogen.ota.activities.AddonActivity;
import com.nitrogen.ota.activities.AvailableActivity;
import com.nitrogen.ota.tasks.LoadUpdateManifest;
import com.nitrogen.ota.utils.Constants;
import com.nitrogen.ota.utils.Preferences;
import com.nitrogen.ota.utils.Utils;

public class AppReceiver extends BroadcastReceiver implements Constants{

	public final String TAG = this.getClass().getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		Bundle extras = intent.getExtras();
		long mRomDownloadID = Preferences.getDownloadID(context);

		if (action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
			long id = extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID);
			boolean isAddonDownload = false;
			int keyForAddonDownload = 0;

			Set<Integer> set = NitrogenOTA.getAddonDownloadKeySet();
			Iterator<Integer> iterator = set.iterator();

			while (iterator.hasNext() && isAddonDownload != true) {
				int nextValue = iterator.next();
				if (id == NitrogenOTA.getAddonDownload(nextValue)) {
					isAddonDownload = true;
					keyForAddonDownload = nextValue;
					if (DEBUGGING) {
						Log.d(TAG, "Checking ID " + nextValue);
					}
				}
			}

			if (isAddonDownload) {
				DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
				DownloadManager.Query query = new DownloadManager.Query();
				query.setFilterById(id);
				Cursor cursor = downloadManager.query(query);

				// it shouldn't be empty, but just in case
				if (!cursor.moveToFirst()) {
					if (DEBUGGING)
						Log.e(TAG, "Addon Download Empty row");
					return;
				}

				int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
				if (DownloadManager.STATUS_SUCCESSFUL != cursor.getInt(statusIndex)) {
					if (DEBUGGING)
						Log.w(TAG, "Download Failed");
					Log.d(TAG, "Removing Addon download with id " + keyForAddonDownload);
					NitrogenOTA.removeAddonDownload(keyForAddonDownload);
					AddonActivity.AddonsArrayAdapter.updateProgress(keyForAddonDownload, 0, true);
					AddonActivity.AddonsArrayAdapter.updateButtons(keyForAddonDownload, false);
					return;
				} else {
					if (DEBUGGING)
						Log.v(TAG, "Download Succeeded");
					Log.d(TAG, "Removing Addon download with id " + keyForAddonDownload);
					NitrogenOTA.removeAddonDownload(keyForAddonDownload);
					AddonActivity.AddonsArrayAdapter.updateButtons(keyForAddonDownload, true);
					return;
				}
			} else {
				if (DEBUGGING)
					Log.v(TAG, "Receiving " + mRomDownloadID);

				if (id != mRomDownloadID) {
					if (DEBUGGING)
						Log.v(TAG, "Ignoring unrelated non-ROM download " + id);
					return;
				}

				DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
				DownloadManager.Query query = new DownloadManager.Query();
				query.setFilterById(id);
				Cursor cursor = downloadManager.query(query);

				// it shouldn't be empty, but just in case
				if (!cursor.moveToFirst()) {
					if (DEBUGGING)
						Log.e(TAG, "Rom download Empty row");
					return;
				}

				int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
				if (DownloadManager.STATUS_SUCCESSFUL != cursor.getInt(statusIndex)) {
					if (DEBUGGING)
						Log.w(TAG, "Download Failed");
					Preferences.setDownloadFinished(context, false);
					if (Utils.isLollipop()) {
						AvailableActivity.setupMenuToolbar(context); // Reset options menu
					} else {
						AvailableActivity.invalidateMenu();
					}
					return;
				} else {
					if (DEBUGGING)
						Log.v(TAG, "Download Succeeded");
					Preferences.setDownloadFinished(context, true);
					AvailableActivity.setupProgress(context);
					if (Utils.isLollipop()) {
						AvailableActivity.setupMenuToolbar(context); // Reset options menu
					} else {
						AvailableActivity.invalidateMenu();
					}
					return;
				}
			}
		}

		if (action.equals(DownloadManager.ACTION_NOTIFICATION_CLICKED)) {

			long[] ids = extras.getLongArray(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS);

			for (long id : ids) {
				if (id != mRomDownloadID) {
					if (DEBUGGING)
						Log.v(TAG, "mDownloadID is " + mRomDownloadID + " and ID is " + id);
					return;
				} else {
					Intent i = new Intent(context, AvailableActivity.class);
					i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					context.startActivity(i);
				}
			}
		}

		if (action.equals(MANIFEST_CHECK_BACKGROUND)) {
			if (DEBUGGING)
				Log.d(TAG, "Receiving background check confirmation");

			boolean updateAvailable = RomUpdate.getUpdateAvailability(context);
			String filename = RomUpdate.getFilename(context);

			if (updateAvailable) {
				Utils.setupNotification(context, filename);
				Utils.scheduleNotification(context, !Preferences.getBackgroundService(context));
			}
		}

		if (action.equals(START_UPDATE_CHECK)) {
			if (DEBUGGING)
				Log.d(TAG, "Update check started");
			new LoadUpdateManifest(context, false).execute();
		}

		if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
			if (DEBUGGING) {
				Log.d(TAG, "Boot received");
			}
			boolean backgroundCheck = Preferences.getBackgroundService(context);
			if (backgroundCheck) {
				if (DEBUGGING)
					Log.d(TAG, "Starting background check alarm");
				Utils.scheduleNotification(context, !Preferences.getBackgroundService(context));
			}
		}

		if (action.equals(IGNORE_RELEASE)) {
			if (DEBUGGING) {
				Log.d(TAG, "Ignore release");
			}
			Preferences.setIgnoredRelease(context, Integer.toString(RomUpdate.getVersionNumber(context)));
			final NotificationManager mNotifyManager =
					(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			Builder mBuilder = new NotificationCompat.Builder(context);
			mBuilder.setContentTitle(context.getString(R.string.main_release_ignored))
			.setSmallIcon(R.drawable.ic_notif)
			.setContentIntent(PendingIntent.getActivity(context, 0, new Intent(), 0));
			mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());

			Handler h = new Handler();
			long delayInMilliseconds = 1500;
			h.postDelayed(new Runnable() {

				public void run() {
					mNotifyManager.cancel(NOTIFICATION_ID);
				}}, delayInMilliseconds);
		}
	}
}


