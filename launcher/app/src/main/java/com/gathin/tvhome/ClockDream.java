package com.gathin.tvhome;

import android.service.dreams.DreamService;

/** Theme-backed ambient photo frame with a drifting clock and local captions; it needs no network. */
public class ClockDream extends DreamService {

    private PhotoFrameScene scene;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);
        scene = new PhotoFrameScene(this, getWindow().getDecorView().findViewById(R.id.dreamRoot), Config.load(this));
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        scene.start();
    }

    @Override
    public void onDreamingStopped() {
        scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.close();
        super.onDetachedFromWindow();
    }
}
