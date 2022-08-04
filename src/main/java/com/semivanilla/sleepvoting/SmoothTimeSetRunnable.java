package com.semivanilla.sleepvoting;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class SmoothTimeSetRunnable extends BukkitRunnable {
    private final World world;
    private final long addTicks;

    public SmoothTimeSetRunnable(World world, long addTicks) {
        this.world = world;
        this.addTicks = addTicks;
    }

    @Override
    public void run() {
        long time = world.getTime();
        long resultTime = time + addTicks;
        boolean shouldCancel = false;
        if (resultTime > SleepVoting.getInstance().getNightEnd()) {
            resultTime = SleepVoting.getInstance().getNightEnd();
            shouldCancel = true;
        }
        world.setTime(resultTime);

        if (shouldCancel) {
            SleepVoting.getInstance().setSkippingNight(false);
            SleepVoting.getInstance().resetWeather(world);
            cancel();
        }
    }
}
