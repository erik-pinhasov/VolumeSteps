package com.volumesteps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;

public class AudioSessionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1);
        if (sessionId <= 0) return;
        if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction()))
            VolumeKeyService.attachAudioSession(context, sessionId);
        else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction()))
            VolumeKeyService.detachAudioSession(context, sessionId);
    }
}
