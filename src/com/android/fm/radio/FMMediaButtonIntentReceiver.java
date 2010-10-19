/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.fm.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.util.Log;

/**
 *
 */
public class FMMediaButtonIntentReceiver extends BroadcastReceiver {

    private final static String LOGTAG = "FMMediaButtonIntentReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            Log.d(LOGTAG,"ACTION_MEDIA_BUTTON Intent received");

            KeyEvent event = (KeyEvent)
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

            if (event == null) {
                return;
            }

            int keycode = event.getKeyCode();
            int action = event.getAction();

            if (action == KeyEvent.ACTION_DOWN) {
                String command = null;

                switch (keycode) {
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        command = FMRadioService.CMDTOGGLEPAUSE;
                        break;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        command = FMRadioService.CMDNEXT;
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        command = FMRadioService.CMDPREVIOUS;
                        break;
                }
                Log.d(LOGTAG, "KeyEvent received: "+command);

                if (command != null) {
                    Log.d(LOGTAG, "Preparing to broadcast Intent to FMRadioService");

                    // The service may or may not be running, but we need to send it
                    // a command.
                    Intent i = new Intent(context, FMRadioService.class);
                    i.setAction(FMRadioService.SERVICECMD);
                    i.putExtra(FMRadioService.CMDNAME, command);

                    Log.d(LOGTAG, "Broadcasting Intent -> "+i.toString());
                    context.startService(i);
                }
            }

            if (isOrderedBroadcast()) {
                abortBroadcast();
            }
        }
    }
}
