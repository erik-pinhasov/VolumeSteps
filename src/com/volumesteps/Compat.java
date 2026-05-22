package com.volumesteps;

import android.content.Context;
import android.os.Build;
import android.os.Vibrator;

public class Compat {

    public static void vibrateTick(Vibrator vibrator) {
        if (vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                Class<?> ve = Class.forName("android.os.VibrationEffect");
                Object fx = ve.getMethod("createPredefined", int.class).invoke(null, 2);
                Vibrator.class.getMethod("vibrate", ve).invoke(vibrator, fx);
            } else if (Build.VERSION.SDK_INT >= 26) {
                Class<?> ve = Class.forName("android.os.VibrationEffect");
                Object fx = ve.getMethod("createOneShot", long.class, int.class).invoke(null, 10L, -1);
                Vibrator.class.getMethod("vibrate", ve).invoke(vibrator, fx);
            } else {
                vibrator.vibrate(10);
            }
        } catch (Exception e) {}
    }

    public static Vibrator getVibrator(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Object vm = context.getSystemService("vibrator_manager");
                if (vm != null) return (Vibrator) vm.getClass().getMethod("getDefaultVibrator").invoke(vm);
            }
        } catch (Exception e) {}
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }
}
