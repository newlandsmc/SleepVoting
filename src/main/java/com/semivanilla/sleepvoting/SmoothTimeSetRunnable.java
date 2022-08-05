package com.semivanilla.sleepvoting;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class SmoothTimeSetRunnable extends BukkitRunnable {
    private final World world;
    private final long addTicks, nightEnd;

    public SmoothTimeSetRunnable(World world, long addTicks) {
        this.world = world;
        this.addTicks = addTicks;
        this.nightEnd = SleepVoting.getInstance().getNightEnd(world);
    }

    @Override
    public void run() {
        long time = world.getTime();
        long resultTime = time + addTicks;
        boolean shouldCancel = false;
        if (resultTime > nightEnd) {
            resultTime = nightEnd;
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
