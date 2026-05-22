package com.volumesteps;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VolumeStepController {

    private static final String TAG = "VolumeStepCtrl";
    private final Context context;
    private final AudioManager audioManager;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Equalizer> equalizers = new ConcurrentHashMap<>();
    private final int systemMax;
    private int[] perLevelMb;
    private int[][] stepTable;
    private int lastSystemVol = -1;
    private volatile boolean selfChanging = false;
    private long lastStepTime = 0;
    private final List<Runnable> rampRunnables = new ArrayList<>();

    public interface StepListener { void onStepChanged(int step, int total); }
    private final List<StepListener> listeners = new ArrayList<>();
    public void addStepListener(StepListener l) { listeners.add(l); }
    public void removeStepListener(StepListener l) { listeners.remove(l); }

    private void notifyStepChanged() {
        int step = getCurrentStep(), total = getTotalSteps();
        for (StepListener l : listeners) {
            try { l.onStepChanged(step, total); } catch (Exception e) {}
        }
    }

    private final ContentObserver volumeObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            try {
                if (selfChanging) return;
                int sysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (sysVol != lastSystemVol) {
                    lastSystemVol = sysVol;
                    int totalSteps = getTotalSteps();
                    if (sysVol == 0) { setCurrentStep(0); }
                    else {
                        float fraction = (float)(sysVol - 1) / Math.max(systemMax - 1, 1);
                        int step = Math.round(fraction * totalSteps);
                        setCurrentStep(Math.max(1, Math.min(step, totalSteps)));
                    }
                    setAllGain(gainOffsetForStep(getCurrentStep()));
                    notifyStepChanged();
                }
            } catch (Exception e) { Log.w(TAG, "observer error", e); }
        }
    };

    private VolumeStepController(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.prefs = context.getSharedPreferences("volume_steps", Context.MODE_PRIVATE);
        this.systemMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        this.perLevelMb = measurePerLevelMb();
        buildStepTable();
    }

    private static volatile VolumeStepController instance;
    public static VolumeStepController getInstance(Context ctx) {
        if (instance == null) {
            synchronized (VolumeStepController.class) {
                if (instance == null) instance = new VolumeStepController(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    public int getTotalSteps() { return prefs.getInt("total_steps", 200); }
    public int getStepSize() { return prefs.getInt("step_size", 1); }
    public int getCurrentStep() { return prefs.getInt("current_step", 0); }
    private void setCurrentStep(int step) { prefs.edit().putInt("current_step", step).apply(); }

    public void startObserving() {
        try { context.getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver); }
        catch (Exception e) { Log.w(TAG, "observe failed", e); }
    }
    public void stopObserving() {
        try { context.getContentResolver().unregisterContentObserver(volumeObserver); }
        catch (Exception e) {}
    }

    public void attachSession(int sessionId) {
        if (equalizers.containsKey(sessionId)) return;
        try {
            Equalizer eq = new Equalizer(Integer.MAX_VALUE, sessionId);
            eq.setEnabled(true);
            equalizers.put(sessionId, eq);
            applyEqGain(eq, gainOffsetForStep(getCurrentStep()));
        } catch (Exception e) {}
    }

    public void detachSession(int sessionId) {
        Equalizer eq = equalizers.remove(sessionId);
        if (eq != null) { try { eq.setEnabled(false); eq.release(); } catch (Exception e) {} }
    }

    public int stepUp() { setStep(getCurrentStep() + getStepSize()); return getCurrentStep(); }
    public int stepDown() { setStep(getCurrentStep() - getStepSize()); return getCurrentStep(); }

    public void setStep(int step) {
        int totalSteps = getTotalSteps();
        int newStep = Math.max(0, Math.min(step, totalSteps));
        setCurrentStep(newStep);
        if (newStep == 0) { setSystemVolume(0); setAllGain(0); notifyStepChanged(); return; }

        ensureStepTable();
        int targetVol, gainOffset;
        if (newStep < stepTable.length) { targetVol = stepTable[newStep][0]; gainOffset = stepTable[newStep][1]; }
        else { int[] fb = computeFallback(newStep); targetVol = fb[0]; gainOffset = fb[1]; }

        int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (targetVol != currentVol) {
            long now = System.currentTimeMillis();
            for (Runnable r : rampRunnables) handler.removeCallbacks(r);
            rampRunnables.clear();
            if (now - lastStepTime < 100) {
                setAllGain(gainOffset); setSystemVolume(targetVol);
            } else {
                int idx = Math.max(0, Math.min(targetVol - 1, perLevelMb.length - 1));
                int bMb = targetVol > 0 && idx < perLevelMb.length ? perLevelMb[idx] : 300;
                int dir = targetVol > currentVol ? 1 : -1;
                int preGain = gainOffset + (dir * bMb);
                setAllGain(preGain);
                final int fTV = targetVol, fGO = gainOffset;
                Runnable sysR = new Runnable() { @Override public void run() { setSystemVolume(fTV); } };
                rampRunnables.add(sysR); handler.postDelayed(sysR, 5);
                for (int i = 1; i <= 4; i++) {
                    int rg = preGain + (int)((fGO - preGain) * ((float) i / 4));
                    final int frg = rg;
                    Runnable r = new Runnable() { @Override public void run() { setAllGain(frg); } };
                    rampRunnables.add(r); handler.postDelayed(r, 5 + (i * 15));
                }
            }
            lastStepTime = now;
        } else { lastStepTime = System.currentTimeMillis(); setAllGain(gainOffset); }
        notifyStepChanged();
    }

    public void syncFromSystem() {
        int sysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        lastSystemVol = sysVol;
        if (sysVol == 0) { setCurrentStep(0); setAllGain(0); return; }
        ensureStepTable();
        int best = 1;
        for (int i = 1; i < stepTable.length; i++) { if (stepTable[i][0] <= sysVol) best = i; }
        setCurrentStep(best);
        setAllGain(gainOffsetForStep(best));
    }

    private int[] measurePerLevelMb() {
        if (systemMax <= 1) return new int[]{300};
        int[] r = new int[systemMax];
        for (int i = 0; i < systemMax; i++) {
            if (i == 0) { r[i] = 600; }
            else {
                double d1 = 20.0 * Math.log((double)i / systemMax) / Math.log(10.0);
                double d2 = 20.0 * Math.log((double)Math.min(i+1, systemMax) / systemMax) / Math.log(10.0);
                int mb = (int)((d2 - d1) * 100);
                r[i] = mb <= 0 ? 300 : mb;
            }
        }
        return r;
    }

    private void ensureStepTable() {
        int t = getTotalSteps();
        if (stepTable != null && stepTable.length == t + 1) return;
        buildStepTable();
    }

    public void rebuildStepTable() { buildStepTable(); }

    private void buildStepTable() {
        int steps = getTotalSteps();
        if (steps <= 0 || systemMax <= 1) { stepTable = new int[steps+1][2]; return; }
        stepTable = new int[steps+1][2];
        stepTable[0] = new int[]{0, 0};
        for (int s = 1; s <= steps; s++) {
            float frac = (float)s / steps;
            float fSV = 1 + frac * (systemMax - 1);
            int sL = Math.max(1, Math.min((int)Math.ceil(fSV), systemMax));
            int idx = Math.max(0, Math.min(sL, perLevelMb.length - 1));
            float att = sL - fSV;
            stepTable[s] = new int[]{sL, -(int)(att * perLevelMb[idx])};
        }
    }

    private int[] computeFallback(int step) {
        int t = getTotalSteps();
        float frac = (float)step / t, fSV = 1 + frac * (systemMax - 1);
        int sV = Math.max(1, Math.min((int)Math.ceil(fSV), systemMax));
        long sum = 0; for (int m : perLevelMb) sum += m;
        int avg = perLevelMb.length > 0 ? (int)(sum / perLevelMb.length) : 300;
        return new int[]{sV, -(int)((sV - fSV) * avg)};
    }

    private int gainOffsetForStep(int step) {
        if (step <= 0) return 0;
        ensureStepTable();
        return step < stepTable.length ? stepTable[step][1] : computeFallback(step)[1];
    }

    private void setSystemVolume(int volume) {
        selfChanging = true;
        try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0); } catch (Exception e) {}
        lastSystemVol = volume;
        selfChanging = false;
    }

    private void setAllGain(int mb) { for (Equalizer eq : equalizers.values()) applyEqGain(eq, mb); }

    private void applyEqGain(Equalizer eq, int mb) {
        try {
            short[] range = eq.getBandLevelRange();
            short clamped = (short) Math.max(range[0], Math.min(mb, range[1]));
            short n = eq.getNumberOfBands();
            for (short i = 0; i < n; i++) eq.setBandLevel(i, clamped);
        } catch (Exception e) {}
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        rampRunnables.clear();
        try { stopObserving(); } catch (Exception e) {}
        for (Equalizer eq : equalizers.values()) {
            try { eq.setEnabled(false); eq.release(); } catch (Exception e) {}
        }
        equalizers.clear();
        listeners.clear();
        synchronized (VolumeStepController.class) { instance = null; }
    }
}
