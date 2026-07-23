package com.sxilverr.enhancedcelestialstweaks;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ECTweaksClientConfig {

    public static final General GENERAL = new General();
    public static final Map<String, EventClient> EVENTS = new LinkedHashMap<>();
    static {
        for (String key : ECTweaksConfig.EVENT_DEFAULTS.keySet()) {
            EVENTS.put(key, new EventClient(key));
        }
    }

    public static final class General {
        public String sleepPreventedMessage = "You may not rest now because the current lunar event prevents it.";
    }

    public static final class EventClient {
        public String fogColor = "";
        public double fogDensityMultiplier = 1.0;
        public String soundtrack;
        public double soundtrackVolume = 1.0;
        public double soundtrackPitch = 1.0;
        public boolean soundtrackLoop = true;

        public EventClient(String eventName) {
            this.soundtrack = ECTweaksConfig.EVENT_DEFAULTS.get(eventName).soundtrack();
        }
    }

    private ECTweaksClientConfig() {
    }
}
