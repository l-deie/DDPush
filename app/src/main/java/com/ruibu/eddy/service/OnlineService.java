package com.ruibu.eddy.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.widget.Toast;

import com.ruibu.eddy.DateTimeUtil;
import com.ruibu.eddy.Params;
import com.ruibu.eddy.Util;
import com.ruibu.eddy.ddpush.MainActivity;
import com.ruibu.eddy.ddpush.R;
import com.ruibu.eddy.receiver.TickAlarmReceiver;

import org.ddpush.im.v1.client.appuser.Message;
import org.ddpush.im.v1.client.appuser.TCPClientBase;

import java.nio.ByteBuffer;

/**
 * Created by Eddy on 2015/3/1.
 */
public class OnlineService  extends Service {

    protected PendingIntent tickPendIntent;
    PowerManager.WakeLock wakeLock;
    CRTcpClient cRTcpClient;
    Notification n;

    public OnlineService() {
    }

    public class  CRTcpClient extends TCPClientBase{

        public CRTcpClient(byte[] uuid, int appid, String serverAddr, int serverPort) throws Exception {
            super(uuid, appid, serverAddr, serverPort, 10);
        }

        @Override
        public boolean hasNetworkConnection() {
            return Util.hasNetwork(OnlineService.this);
        }

        @Override
        public void trySystemSleep() {

            if(wakeLock != null && wakeLock.isHeld() == true){
                wakeLock.release();
            }

        }

        @Override
        public void onPushMessage(Message message) {

            if(message == null){
                return;
            }
            if(message.getData() == null || message.getData().length == 0){
                return;
            }
            if(message.getCmd() == 16){// 0x10 通用推送信息
                notifyUser(16,"DDPush通用推送信息","时间："+ DateTimeUtil.getCurDateTime(),"收到通用推送信息");
            }
            if(message.getCmd() == 17){// 0x11 分组推送信息
                long msg = ByteBuffer.wrap(message.getData(), 5, 8).getLong();
                notifyUser(17,"DDPush分组推送信息",""+msg,"收到通用推送信息");
            }
            if(message.getCmd() == 32){// 0x20 自定义推送信息
                String str = null;
                try{
                    str = new String(message.getData(),5,message.getContentLength(), "UTF-8");
                }catch(Exception e){
                    str = Util.convert(message.getData(),5,message.getContentLength());
                }
                notifyUser(32,"DDPush自定义推送信息",""+str,"收到自定义推送信息");
            }
            setPkgsInfo();

        }
    }

    protected void cancelNotifyRunning(){
        NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(0);
    }

    protected void cancelTickAlarm(){
        AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(tickPendIntent);
    }

    public void notifyUser(int id, String title, String content, String tickerText){
        NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification n = new Notification();
        Intent intent = new Intent(this,MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0,intent, PendingIntent.FLAG_ONE_SHOT);
        n.contentIntent = pi;

        n.setLatestEventInfo(this, title, content, pi);
        n.defaults = Notification.DEFAULT_ALL;
        n.flags |= Notification.FLAG_SHOW_LIGHTS;
        n.flags |= Notification.FLAG_AUTO_CANCEL;

        n.icon = R.drawable.ic_launcher;
        n.when = System.currentTimeMillis();
        n.tickerText = tickerText;
        notificationManager.notify(id, n);
    }


    protected void tryReleaseWakeLock(){
        if(wakeLock != null && wakeLock.isHeld() == true){
            wakeLock.release();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //this.cancelTickAlarm();
        cancelNotifyRunning();
        this.tryReleaseWakeLock();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.setTickAlarm();

        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OnlineService");

        resetClient();

        notifyRunning();
    }


    @Override
    public int onStartCommand(Intent param, int flags, int startId) {
        if(param == null){
            return START_STICKY;
        }
        String cmd = param.getStringExtra("CMD");
        if(cmd == null){
            cmd = "";
        }
        if(cmd.equals("TICK")){
            if(wakeLock != null && wakeLock.isHeld() == false){
                wakeLock.acquire();
            }
        }
        if(cmd.equals("RESET")){
            if(wakeLock != null && wakeLock.isHeld() == false){
                wakeLock.acquire();
            }
            resetClient();
        }
        if(cmd.equals("TOAST")){
            String text = param.getStringExtra("TEXT");
            if(text != null && text.trim().length() != 0){
                Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            }
        }

        setPkgsInfo();

        return START_STICKY;
    }

    protected void setPkgsInfo(){
        if(this.cRTcpClient == null){
            return;
        }
        long sent = cRTcpClient.getSentPackets();
        long received = cRTcpClient.getReceivedPackets();
        SharedPreferences account = this.getSharedPreferences(Params.DEFAULT_PRE_NAME,Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = account.edit();
        editor.putString(Params.SENT_PKGS, ""+sent);
        editor.putString(Params.RECEIVE_PKGS, ""+received);
        editor.commit();
    }


    protected void setTickAlarm(){
        AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this,TickAlarmReceiver.class);
        int requestCode = 0;
        tickPendIntent = PendingIntent.getBroadcast(this,
                requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        //小米2s的MIUI操作系统，目前最短广播间隔为5分钟，少于5分钟的alarm会等到5分钟再触发！2014-04-28
        long triggerAtTime = System.currentTimeMillis();
        int interval = 300 * 1000;
        alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, interval, tickPendIntent);
    }


    protected void resetClient(){
        SharedPreferences account = this.getSharedPreferences(Params.DEFAULT_PRE_NAME,Context.MODE_PRIVATE);
        String serverIp = account.getString(Params.SERVER_IP, "");
        String serverPort = account.getString(Params.SERVER_PORT, "");
        String pushPort = account.getString(Params.PUSH_PORT, "");
        String userName = account.getString(Params.USER_NAME, "");
        if(serverIp == null || serverIp.trim().length() == 0
                || serverPort == null || serverPort.trim().length() == 0
                || pushPort == null || pushPort.trim().length() == 0
                || userName == null || userName.trim().length() == 0){
            return;
        }
        if(this.cRTcpClient != null){
            try{cRTcpClient.stop();}catch(Exception e){}
        }
        try{
            cRTcpClient = new CRTcpClient(Util.md5Byte(userName), 1, serverIp, Integer.parseInt(serverPort));
            cRTcpClient.setHeartbeatInterval(50);
            cRTcpClient.start();
            SharedPreferences.Editor editor = account.edit();
            editor.putString(Params.SENT_PKGS, "0");
            editor.putString(Params.RECEIVE_PKGS, "0");
            editor.commit();
        }catch(Exception e){
            Toast.makeText(this.getApplicationContext(), "操作失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        Toast.makeText(this.getApplicationContext(), "ddpush：终端重置", Toast.LENGTH_LONG).show();
    }

    protected void notifyRunning(){
        NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
        n = new Notification();
        Intent intent = new Intent(this,MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0,intent, PendingIntent.FLAG_ONE_SHOT);
        n.contentIntent = pi;
        n.setLatestEventInfo(this, "DDPushDemoTCP", "正在运行", pi);
        //n.defaults = Notification.DEFAULT_ALL;
        //n.flags |= Notification.FLAG_SHOW_LIGHTS;
        //n.flags |= Notification.FLAG_AUTO_CANCEL;
        n.flags |= Notification.FLAG_ONGOING_EVENT;
        n.flags |= Notification.FLAG_NO_CLEAR;
        //n.iconLevel = 5;

        n.icon = R.drawable.ic_launcher;
        n.when = System.currentTimeMillis();
        n.tickerText = "DDPushDemoTCP正在运行";
        notificationManager.notify(0, n);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
