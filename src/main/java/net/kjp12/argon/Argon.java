package net.kjp12.argon;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kjp12.argon.helpers.SubServer;
import net.minecraft.util.profiler.ProfileResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Argon {
    public static final Logger logger = LogManager.getLogger("Argon");
    @Environment(EnvType.CLIENT)
    public static boolean debugInternalServer = false;
    public static volatile SubServer serverToProfile = null;
    public static volatile ProfileResult profileResult = null;
    public static String profileSection = "root";

    public static void handleProfilerKeyPress(int digit) {
        if (profileResult != null) {
            var list = profileResult.getTimings(profileSection);
            if (!list.isEmpty()) {
                var profilerTiming = list.remove(0);
                if (digit == 0) {
                    if (!profilerTiming.name.isEmpty()) {
                        int i = profileSection.lastIndexOf(30);
                        if (i >= 0) profileSection = profileSection.substring(0, i);
                    }
                } else {
                    --digit;
                    if (digit < list.size() && !"unspecified".equals(list.get(digit).name)) {
                        if (!profileSection.isEmpty()) profileSection += '\u001e';
                        profileSection += list.get(digit).name;
                    }
                }
            }
        }
    }
}
