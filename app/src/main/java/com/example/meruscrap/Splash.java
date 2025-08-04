package com.example.meruscrap;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

public class Splash extends AppCompatActivity {

    private static final int SPLASH_DURATION = 11500; // 3.5 seconds
    private static final int ANIMATION_DELAY = 600;

    private CardView logoCard, posCard;
    private TextView companyName, companySubtitle, loadingText;
    private View loadingContainer, bottomBranding;
    private ProgressBar progressBar;
    private Handler handler;
    private ValueAnimator progressAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
// Set status bar color using gradient center color directly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.gradient_center));

            // Ensure status bar icons are light/white (for dark backgrounds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(0); // Clear any flags to keep icons light
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.background_medium));
        }
        // Make the splash screen fullscreen and edge-to-edge
        setupFullscreenMode();

        setContentView(R.layout.activity_splash);

        // Initialize handler first
        handler = new Handler(Looper.getMainLooper());

        initializeViews();
        startAnimations();

        // Navigate to main activity after splash duration
        handler.postDelayed(this::navigateToMainActivity, SPLASH_DURATION);
    }

    private void setupFullscreenMode() {
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Set status bar and navigation bar to transparent
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        // Keep screen on during splash
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void initializeViews() {
        logoCard = findViewById(R.id.logoCard);
        posCard = findViewById(R.id.posCard);
        companyName = findViewById(R.id.companyName);
        companySubtitle = findViewById(R.id.companySubtitle);
        loadingContainer = findViewById(R.id.loadingContainer);
        loadingText = findViewById(R.id.loadingText);
        bottomBranding = findViewById(R.id.bottomBranding);
        progressBar = findViewById(R.id.progressBar);
    }

    private void startAnimations() {
        // Animate logo card with scale and rotation
        animateLogoCard();

        // Animate text elements with stagger
        animateTextElements();

        // Animate loading components
        animateLoadingElements();

        // Animate bottom branding
        animateBottomBranding();
    }

    private void animateLogoCard() {
        // Initial scale animation
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(logoCard, "scaleX", 0f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(logoCard, "scaleY", 0f, 1.2f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(logoCard, "alpha", 0f, 1f);

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(scaleX, scaleY, alpha);
        logoSet.setDuration(800);
        logoSet.setInterpolator(new OvershootInterpolator(1.2f));
        logoSet.start();

        // Subtle floating animation
        handler.postDelayed(() -> {
            ObjectAnimator float1 = ObjectAnimator.ofFloat(logoCard, "translationY", 0f, -10f, 0f);
            float1.setDuration(2000);
            float1.setRepeatCount(ObjectAnimator.INFINITE);
            float1.setRepeatMode(ObjectAnimator.REVERSE);
            float1.setInterpolator(new AccelerateDecelerateInterpolator());
            float1.start();
        }, 800);
    }

    private void animateTextElements() {
        // Company name animation
        handler.postDelayed(() -> {
            ObjectAnimator nameAlpha = ObjectAnimator.ofFloat(companyName, "alpha", 0f, 1f);
            ObjectAnimator nameTransY = ObjectAnimator.ofFloat(companyName, "translationY", 50f, 0f);

            AnimatorSet nameSet = new AnimatorSet();
            nameSet.playTogether(nameAlpha, nameTransY);
            nameSet.setDuration(600);
            nameSet.setInterpolator(new AccelerateDecelerateInterpolator());
            nameSet.start();
        }, ANIMATION_DELAY);

        // Company subtitle animation
        handler.postDelayed(() -> {
            ObjectAnimator subtitleAlpha = ObjectAnimator.ofFloat(companySubtitle, "alpha", 0f, 1f);
            ObjectAnimator subtitleTransY = ObjectAnimator.ofFloat(companySubtitle, "translationY", 30f, 0f);

            AnimatorSet subtitleSet = new AnimatorSet();
            subtitleSet.playTogether(subtitleAlpha, subtitleTransY);
            subtitleSet.setDuration(500);
            subtitleSet.setInterpolator(new AccelerateDecelerateInterpolator());
            subtitleSet.start();
        }, ANIMATION_DELAY + 200);

        // POS card animation
        handler.postDelayed(() -> {
            ObjectAnimator posAlpha = ObjectAnimator.ofFloat(posCard, "alpha", 0f, 1f);
            ObjectAnimator posScale = ObjectAnimator.ofFloat(posCard, "scaleX", 0.8f, 1f);
            ObjectAnimator posScaleY = ObjectAnimator.ofFloat(posCard, "scaleY", 0.8f, 1f);

            AnimatorSet posSet = new AnimatorSet();
            posSet.playTogether(posAlpha, posScale, posScaleY);
            posSet.setDuration(400);
            posSet.setInterpolator(new OvershootInterpolator(1.1f));
            posSet.start();
        }, ANIMATION_DELAY + 400);
    }

    private void animateLoadingElements() {
        handler.postDelayed(() -> {
            ObjectAnimator loadingAlpha = ObjectAnimator.ofFloat(loadingContainer, "alpha", 0f, 1f);
            ObjectAnimator loadingTransY = ObjectAnimator.ofFloat(loadingContainer, "translationY", 20f, 0f);

            AnimatorSet loadingSet = new AnimatorSet();
            loadingSet.playTogether(loadingAlpha, loadingTransY);
            loadingSet.setDuration(400);
            loadingSet.setInterpolator(new AccelerateDecelerateInterpolator());
            loadingSet.start();

            // Start progress bar animation
            startProgressAnimation();

        }, ANIMATION_DELAY + 800);
    }

    private void animateBottomBranding() {
        handler.postDelayed(() -> {
            ObjectAnimator brandingAlpha = ObjectAnimator.ofFloat(bottomBranding, "alpha", 0f, 1f);
            ObjectAnimator brandingTransY = ObjectAnimator.ofFloat(bottomBranding, "translationY", 30f, 0f);

            AnimatorSet brandingSet = new AnimatorSet();
            brandingSet.playTogether(brandingAlpha, brandingTransY);
            brandingSet.setDuration(500);
            brandingSet.setInterpolator(new AccelerateDecelerateInterpolator());
            brandingSet.start();
        }, ANIMATION_DELAY + 1000);
    }

    private void startProgressAnimation() {
        progressAnimator = ValueAnimator.ofInt(0, 100);
        progressAnimator.setDuration(5200); // Slightly less than remaining splash time
        progressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        progressAnimator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            progressBar.setProgress(progress);

            // Update loading text based on progress
            updateLoadingText(progress);
        });

        progressAnimator.start();
    }

    private void updateLoadingText(int progress) {
        String[] loadingMessages = {
                "Initializing...",
                "Loading modules...",
                "Setting up database...",
                "Configuring POS...",
                "Almost ready...",
                "Ready!"
        };

        int messageIndex = Math.min(progress / 17, loadingMessages.length - 1);
        loadingText.setText(loadingMessages[messageIndex]);
    }

    private void navigateToMainActivity() {
        // Add exit animation
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(findViewById(android.R.id.content), "alpha", 1f, 0f);
        fadeOut.setDuration(500);
        fadeOut.start();

        // Navigate to main activity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(Splash.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 300);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.cancel();
        }

        // Clear keep screen on flag
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() {
        // Disable back button during splash screen
        // User should wait for the splash to complete
        super.onBackPressed();
    }
}