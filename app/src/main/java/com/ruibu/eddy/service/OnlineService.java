package com.ruibu.eddy.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.ruibu.eddy.Util;

import org.ddpush.im.v1.client.appuser.Message;
import org.ddpush.im.v1.client.appuser.TCPClientBase;

/**
 * Created by Eddy on 2015/3/1.
 */
public class OnlineService  extends Service {

    public class  CRTcpClient extends TCPClientBase{

        public CRTcpClient(byte[] uuid, int appid, String serverAddr, int serverPort, int connectTimeout) throws Exception {
            super(uuid, appid, serverAddr, serverPort, connectTimeout);
        }

        @Override
        public boolean hasNetworkConnection() {
            return Util.hasNetwork(OnlineService.this);
        }

        @Override
        public void trySystemSleep() {

        }

        @Override
        public void onPushMessage(Message message) {

        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
