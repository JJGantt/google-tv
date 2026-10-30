package com.jaredgantt.tvcompanion;

import android.service.notification.NotificationListenerService;

/** Exists only so the app may read active media sessions (`TvLink.now`): Android allows that to an
 *  enabled notification listener. Granted over adb:
 *    cmd notification allow_listener com.jaredgantt.tvcompanion/.Listener */
public class Listener extends NotificationListenerService {}
