package com.volumesteps;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class VolumeOverlay {
    private static final String TAG = "VolumeOverlay";
    private final Context context;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View overlayView;
    private View trackFill;
    private TextView stepLabel;
    private boolean isShowing = false, isDragging = false;
    private Runnable hideRunnable;
    private int currentStep = 0, totalSteps = 200, trackHeight = 0;

    public interface OnStepSeekListener { void onStepSeek(int step); }
    private OnStepSeekListener seekListener;
    public void setOnStepSeekListener(OnStepSeekListener l) { seekListener = l; }

    public VolumeOverlay(Context ctx) { context = ctx; wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE); }

    public void show(int step, int total) {
        currentStep = step; totalSteps = total;
        handler.post(new Runnable() { @Override public void run() {
            try { showInternal(); } catch (Exception e) { Log.e(TAG, "show", e); }
        }});
    }

    private void showInternal() {
        if (hideRunnable != null) handler.removeCallbacks(hideRunnable);
        if (!isShowing) {
            overlayView = buildView();
            try { wm.addView(overlayView, buildParams()); isShowing = true; }
            catch (Exception e) { Log.e(TAG, "addView", e); return; }
        }
        if (!isDragging) { updateFill(); stepLabel.setText(String.valueOf(currentStep)); }
        if (!isDragging) scheduleHide();
    }

    private View buildView() {
        float d = context.getResources().getDisplayMetrics().density;
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        int barW = (int)(42*d), barH = (int)(screenH*0.38), pad = (int)(6*d);
        trackHeight = barH - pad*2 - (int)(28*d);

        FrameLayout root = new FrameLayout(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xDD1E1E3A); bg.setCornerRadius(14*d); bg.setStroke((int)(1*d), 0x44FFFFFF);
        container.setBackground(bg);

        stepLabel = new TextView(context);
        stepLabel.setTextColor(0xFFCCCCFF);
        stepLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        stepLabel.setGravity(Gravity.CENTER);
        container.addView(stepLabel, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (int)(22*d)));

        FrameLayout trackFrame = new FrameLayout(context);
        LinearLayout.LayoutParams tfp = new LinearLayout.LayoutParams((int)(28*d), trackHeight);
        tfp.gravity = Gravity.CENTER_HORIZONTAL; tfp.topMargin = (int)(4*d);

        View trackBg = new View(context);
        GradientDrawable tbd = new GradientDrawable(); tbd.setColor(0x33FFFFFF); tbd.setCornerRadius(8*d);
        trackBg.setBackground(tbd);
        trackFrame.addView(trackBg, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        trackFill = new View(context);
        GradientDrawable fd = new GradientDrawable(); fd.setColor(0xFF5E9CFF); fd.setCornerRadius(8*d);
        trackFill.setBackground(fd);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 0);
        fp.gravity = Gravity.BOTTOM;
        trackFrame.addView(trackFill, fp);
        container.addView(trackFrame, tfp);

        trackFrame.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isDragging = true; if (hideRunnable != null) handler.removeCallbacks(hideRunnable);
                        handleTouch(v, e.getY()); return true;
                    case MotionEvent.ACTION_MOVE: handleTouch(v, e.getY()); return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        isDragging = false; scheduleHide(); return true;
                }
                return false;
            }
        });

        root.addView(container, new FrameLayout.LayoutParams(barW, barH));
        return root;
    }

    private void handleTouch(View v, float y) {
        int h = v.getHeight(); if (h <= 0) return;
        float frac = Math.max(0f, Math.min(1f, 1f - y/h));
        int step = Math.round(frac * totalSteps);
        currentStep = step; updateFill(); stepLabel.setText(String.valueOf(step));
        if (seekListener != null) seekListener.onStepSeek(step);
    }

    private void updateFill() {
        if (trackFill == null || trackHeight <= 0) return;
        float frac = totalSteps > 0 ? (float)currentStep / totalSteps : 0;
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) trackFill.getLayoutParams();
        p.height = (int)(frac * trackHeight); trackFill.setLayoutParams(p);
    }

    private WindowManager.LayoutParams buildParams() {
        float d = context.getResources().getDisplayMetrics().density;
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            (int)(48*d), (int)(screenH*0.38),
            Build.VERSION.SDK_INT >= 26 ? 2038 : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        p.x = (int)(8*d);
        return p;
    }

    private void scheduleHide() {
        if (hideRunnable != null) handler.removeCallbacks(hideRunnable);
        hideRunnable = new Runnable() { @Override public void run() { hide(); } };
        handler.postDelayed(hideRunnable, 2500);
    }

    public void hide() {
        handler.post(new Runnable() { @Override public void run() {
            if (isDragging) return;
            if (isShowing && overlayView != null) {
                try { wm.removeView(overlayView); } catch (Exception e) {}
                overlayView = null; trackFill = null; stepLabel = null; isShowing = false;
            }
        }});
    }
}
