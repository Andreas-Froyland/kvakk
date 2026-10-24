package app.kvakk.media

import android.service.notification.NotificationListenerService

/**
 * Never reads notifications. Android only lets apps with an enabled notification listener see
 * other apps' media sessions, which the now-playing bar needs.
 */
class NowPlayingListener : NotificationListenerService()
