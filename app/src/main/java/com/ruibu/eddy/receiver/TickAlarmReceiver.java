package com.ruibu.eddy.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import com.ruibu.eddy.Util;
import com.ruibu.eddy.service.OnlineService;

/**
 * Created by Eddy on 2015/3/1.
 */
public class TickAlarmReceiver extends BroadcastReceiver {

    PowerManager.WakeLock wakeLock;

    public TickAlarmReceiver() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(Util.hasNetwork(context) == false){
            return;
        }
        Intent startSrv = new Intent(context, OnlineService.class);
        startSrv.putExtra("CMD", "TICK");
        context.startService(startSrv);
    }

}
