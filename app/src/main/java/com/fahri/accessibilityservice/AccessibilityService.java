package com.fahri.accessibilityservice;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.AudioManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;

import java.util.ArrayDeque;
import java.util.Deque;

public class AccessibilityService extends android.accessibilityservice.AccessibilityService {

    private static final String TAG = "AccessibilityServiceExample";

    private FrameLayout mLayout;
    private WindowManager windowManager;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.e(TAG, "onAccessibilityEvent: ");
        CharSequence packageNameChar = event.getPackageName();
        if (packageNameChar == null) {
            Log.e(TAG, "Package name is null in accessibility event");
            return;
        }
        String packageName = packageNameChar.toString();
        PackageManager packageManager = getPackageManager();
        // check  if package exists
        if (isPackageInstalled(packageName, packageManager)) {
            try {
                ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
                CharSequence applicationLabel = packageManager.getApplicationLabel(applicationInfo);
                Log.e(TAG, "app name is: " + applicationLabel);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to get application info for package: " + packageName, e);
            }
        } else {
            Log.e(TAG, "Package " + packageName + " is not installed");
        }
    }

    /**
     * check if app is installed
     * check if package
     * @param packageName check the package name
     * @param packageManager PackageManager instance
     * @return if app is installed, return true，else return false
     */
    private boolean isPackageInstalled(String packageName, PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onInterrupt() {
        Log.e(TAG, "Something went wrong");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED | AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mLayout = new FrameLayout(this);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // use Gravity.START instead of Gravity.LEFT to support RTL layouts
        layoutParams.gravity = Gravity.START;
        LayoutInflater inflater = LayoutInflater.from(this);
        inflater.inflate(R.layout.floating_bar, mLayout);
        windowManager.addView(mLayout, layoutParams);

        configurePowerButton();
        configureSimulateTouch();
        configureVolumeUpButton();
        configureVolumeDownButton();
        configureScrollUpButton();
        configureScrollDownButton();
        configureSwipeRightButton();
        configureSwipeLeftButton();
    }

    private void configurePowerButton() {
        Button power = mLayout.findViewById(R.id.power);

        // use Lambda expression to simplify code
        power.setOnClickListener(v -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG));
    }

    private void configureVolumeUpButton() {
        Button volumeUpButton = mLayout.findViewById(R.id.volume_up);
        volumeUpButton.setOnClickListener(view -> {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        });
    }

    private void configureVolumeDownButton() {
        Button volumeDownButton = mLayout.findViewById(R.id.volume_down);
        volumeDownButton.setOnClickListener(view -> {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        });
    }

    private void configureSimulateTouch() {
        Button btnSimulateTouch = mLayout.findViewById(R.id.simulateTouch);
        btnSimulateTouch.setOnClickListener(v -> {
            Log.e(TAG, "onClick: Simulate Touch");
            Path tap = new Path();
            tap.moveTo(110, 150);
            GestureDescription.Builder tapBuilder = new GestureDescription.Builder();
            tapBuilder.addStroke(new GestureDescription.StrokeDescription(tap, 0, 500));
            dispatchGesture(tapBuilder.build(), null, null);
        });
    }

    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo root) {
        Deque<AccessibilityNodeInfo> deque = new ArrayDeque<>();
        deque.add(root);
        while (!deque.isEmpty()) {
            AccessibilityNodeInfo node = deque.removeFirst();
            try {
                if (node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
                        || node.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)) {
                    // Create a sealed copy of the node before returning
                    AccessibilityNodeInfo copy = AccessibilityNodeInfo.obtain(node);
                    return copy;
                }
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        deque.addLast(child);
                    }
                }
            } finally {
                if (node != root) {
                    // Recycle the node to avoid memory leaks
                    node.recycle();
                }
            }
        }
        return null;
    }

    private void configureScrollDownButton() {
        Button scrollButton = mLayout.findViewById(R.id.scroll_down);
        scrollButton.setOnClickListener(view -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return;
            }
            // Create a copy of the root node
            AccessibilityNodeInfo rootSnapshot = AccessibilityNodeInfo.obtain(root);
            try {
                AccessibilityNodeInfo scrollable = findScrollableNode(rootSnapshot);
                if (scrollable != null) {
                    try {
                        // Perform the action on the sealed copy
                        scrollable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId());
                    } finally {
                        // Recycle the scrollable node
                        scrollable.recycle();
                    }
                }
            } finally {
                // Recycle the root snapshot
                rootSnapshot.recycle();
            }
        });
    }

    private void configureScrollUpButton() {
        Button scrollButton = mLayout.findViewById(R.id.scroll_up);
        scrollButton.setOnClickListener(view -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return;
            }
            // Create a copy of the root node
            AccessibilityNodeInfo rootSnapshot = AccessibilityNodeInfo.obtain(root);
            try {
                AccessibilityNodeInfo scrollable = findScrollableNode(rootSnapshot);
                if (scrollable != null) {
                    try {
                        // Perform the action on the sealed copy
                        scrollable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.getId());
                    } finally {
                        // Recycle the scrollable node
                        scrollable.recycle();
                    }
                }
            } finally {
                // Recycle the root snapshot
                rootSnapshot.recycle();
            }
        });
    }

    private void configureSwipeRightButton() {
        Button swipeButton = mLayout.findViewById(R.id.swipe_right);
        swipeButton.setOnClickListener(view -> {
            Point size = new Point();
            windowManager.getDefaultDisplay().getSize(size);
            int screenWidth = size.x;
            int screenHeight = size.y;

            // Define the start and end points of the swipe gesture
            // modify startX and endX to make the swipe from right to left
            int startX = (int) (screenWidth * 0.95);
            int endX = (int) (screenWidth * 0.2);
            int y = screenHeight / 10;

            Path swipePath = new Path();
            swipePath.moveTo(startX, y);
            swipePath.lineTo(endX, y);
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 500));
            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.e(TAG, "Swipe right gesture completed");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.e(TAG, "Swipe right gesture cancelled");
                }
            }, null);
        });
    }

    private void configureSwipeLeftButton() {
        Button swipeButton = mLayout.findViewById(R.id.swipe_left);
        swipeButton.setOnClickListener(view -> {
            Point size = new Point();
            windowManager.getDefaultDisplay().getSize(size);
            int screenWidth = size.x;
            int screenHeight = size.y;

            // modify startX and endX to make the swipe from left to right
            int startX = (int) (screenWidth * 0.2);
            int endX = (int) (screenWidth * 0.95);
            int y = screenHeight / 10;

            Path swipePath = new Path();
            swipePath.moveTo(startX, y);
            swipePath.lineTo(endX, y);
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 500));

            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.e(TAG, "Swipe left gesture completed");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.e(TAG, "Swipe left gesture cancelled");
                }
            }, null);
        });
    }
}