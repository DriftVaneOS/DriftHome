package org.driftvane.launcher

import android.service.notification.NotificationListenerService

/**
 * Lets DriftHome see which media is playing (for "Now playing" under the clock).
 * It reads nothing else. Android only allows media-session access through a
 * notification listener, so this needs "Notification access" for DriftHome.
 */
class MediaListener : NotificationListenerService()
