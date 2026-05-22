package com.volumesteps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private SharedPreferences prefs;
    private EditText stepsInput, stepSizeInput;
    private TextView statusText, accessibilityStatus, overlayStatus, volumePreviewText;
    private Button toggleBtn;
    private SeekBar volumePreview;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("volume_steps", Context.MODE_PRIVATE);
        stepsInput = (EditText) findViewById(R.id.steps_input);
        stepSizeInput = (EditText) findViewById(R.id.stepsize_input);
        statusText = (TextView) findViewById(R.id.status_text);
        accessibilityStatus = (TextView) findViewById(R.id.accessibility_status);
        overlayStatus = (TextView) findViewById(R.id.overlay_status);
        toggleBtn = (Button) findViewById(R.id.toggle_btn);
        volumePreview = (SeekBar) findViewById(R.id.volume_preview);
        volumePreviewText = (TextView) findViewById(R.id.volume_preview_text);
        stepsInput.setText(String.valueOf(prefs.getInt("total_steps", 200)));
        stepSizeInput.setText(String.valueOf(prefs.getInt("step_size", 1)));

        findViewById(R.id.apply_btn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applySteps(); } });
        findViewById(R.id.apply_stepsize_btn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyStepSize(); } });
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleEnabled(); } });
        findViewById(R.id.accessibility_btn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } });
        findViewById(R.id.restricted_btn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "Settings > Apps > VolumeSteps > menu > Allow restricted settings", Toast.LENGTH_LONG).show(); }
            } });
        findViewById(R.id.overlay_btn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); } catch (Exception e) {}
            } });
        setupVolumePreview(prefs.getInt("total_steps", 200));
    }

    @Override protected void onResume() { super.onResume(); updateStatus(); }

    private void applySteps() {
        int v = parseInput(stepsInput, 15, 1000); if (v < 0) return;
        prefs.edit().putInt("total_steps", v).apply();
        try { VolumeStepController c = VolumeStepController.getInstance(this); c.rebuildStepTable(); c.syncFromSystem(); } catch (Exception e) {}
        setupVolumePreview(v); Toast.makeText(this, "Total steps: " + v, Toast.LENGTH_SHORT).show(); updateStatus();
    }
    private void applyStepSize() {
        int v = parseInput(stepSizeInput, 1, 50); if (v < 0) return;
        prefs.edit().putInt("step_size", v).apply();
        Toast.makeText(this, "Step size: " + v, Toast.LENGTH_SHORT).show(); updateStatus();
    }
    private int parseInput(EditText input, int min, int max) {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) { Toast.makeText(this, "Enter a number", Toast.LENGTH_SHORT).show(); return -1; }
        try { int v = Integer.parseInt(t);
            if (v < min || v > max) { Toast.makeText(this, "Between " + min + " and " + max, Toast.LENGTH_SHORT).show(); return -1; }
            return v;
        } catch (NumberFormatException e) { Toast.makeText(this, "Invalid", Toast.LENGTH_SHORT).show(); return -1; }
    }
    private void toggleEnabled() {
        boolean was = prefs.getBoolean("enabled", false);
        prefs.edit().putBoolean("enabled", !was).apply();
        Toast.makeText(this, was ? "Disabled" : "Enabled", Toast.LENGTH_SHORT).show(); updateStatus();
    }
    private boolean isAccessibilityEnabled() {
        try { String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(getPackageName() + "/"); } catch (Exception e) { return false; }
    }
    private void updateStatus() {
        boolean en = prefs.getBoolean("enabled", false);
        int steps = prefs.getInt("total_steps", 200), sz = prefs.getInt("step_size", 1);
        boolean acc = isAccessibilityEnabled(), ovl = Settings.canDrawOverlays(this);
        statusText.setText("Steps: " + steps + "  |  Size: " + sz + (acc && en ? "  \u2022  Active" : "  \u2022  Inactive"));
        toggleBtn.setText(acc ? (en ? "Disable" : "Enable") : "Enable accessibility first");
        toggleBtn.setBackgroundColor(acc ? (en ? 0xFFF44336 : 0xFF4CAF50) : 0xFF555555);
        accessibilityStatus.setText(acc ? "\u2713 Accessibility service enabled" : "\u2717 Accessibility service NOT enabled");
        accessibilityStatus.setTextColor(acc ? 0xFF4CAF50 : 0xFFF44336);
        View rB = findViewById(R.id.restricted_btn), rH = findViewById(R.id.restricted_hint);
        if (Build.VERSION.SDK_INT >= 33 && !acc) { rB.setVisibility(View.VISIBLE); rH.setVisibility(View.VISIBLE); }
        else { rB.setVisibility(View.GONE); rH.setVisibility(View.GONE); }
        overlayStatus.setText(ovl ? "\u2713 Overlay permission granted" : "\u2717 Overlay permission needed");
        overlayStatus.setTextColor(ovl ? 0xFF4CAF50 : 0xFFF44336);
        findViewById(R.id.overlay_btn).setVisibility(ovl ? View.GONE : View.VISIBLE);
    }
    private void setupVolumePreview(final int totalSteps) {
        volumePreview.setMax(totalSteps);
        int cur = Math.min(prefs.getInt("current_step", 0), totalSteps);
        volumePreview.setProgress(cur);
        volumePreviewText.setText("Step " + cur + " / " + totalSteps);
        volumePreview.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fu) {
                volumePreviewText.setText("Step " + p + " / " + totalSteps);
                if (fu) { try { VolumeStepController.getInstance(MainActivity.this).setStep(p); } catch (Exception e) {} }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }
}
