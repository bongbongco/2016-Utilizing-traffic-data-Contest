package com.github.bongbongco.asurada.notification;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.BitmapFactory;

import com.github.bongbongco.asurada.R;

/**
 * Created by seungyonglee on 2016. 9. 14..
 */
public class AppNotification extends Activity {

    Intent notificationIntent = new Intent(this, AppNotification.class);
    notificationIntent.putExtra("notificationId", 9999); // 전달 값


    Resources res = getResources();


    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    Notification.Builder builder = new Notification.Builder(this)
            .setContentTitle("졸음방지 어플리케이션 타이틀")
            .setContentText("졸음방지 어플리케이션 내용")
            .setTicker("한줄 메시지")
            .setSmallIcon(R.drawable.asurada)
            .setLargeIcon(BitmapFactory.decodeResource(res, R.drawable.asurada))
            .setContentIntent(MAIN)

}
