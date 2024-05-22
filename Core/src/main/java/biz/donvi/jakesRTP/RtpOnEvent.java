package biz.donvi.jakesRTP;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.RegisteredListener;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.WeakHashMap;
import java.util.logging.Level;

import static biz.donvi.jakesRTP.GeneralUtil.isAnchorSpawn;

public class RtpOnEvent implements Listener {

    private final RandomTeleporter randomTeleporter;

    private final WeakHashMap<PlayerSpawnLocationEvent, Location>    oldSpawnLocationEvents    = new WeakHashMap<>();
    private final WeakHashMap<PlayerRespawnEvent, Location> oldRespawnEvents = new WeakHashMap<>();

    public RtpOnEvent(RandomTeleporter randomTeleporter) {this.randomTeleporter = randomTeleporter;}


    /**
     * When {@code firstJoinRtp} is enabled, this will RTP a player when they join the server
     * for the first time.
     *
     * @param event The PlayerSpawnLocationEvent
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerSpawnLocation(PlayerSpawnLocationEvent event) {
        // If any of these are true, no nothing.
        if (!randomTeleporter.firstJoinRtp ||  // Rtp-on-first-join is DISABLED
            event.getPlayer().hasPermission("jakesrtp.nofirstjoinrtp") // The player is exempt
        ) return;
        try {
            assert randomTeleporter.firstJoinSettings != null;
            assert randomTeleporter.firstJoinSettings.landingWorld != null;
            Location landingLoc = new RandomTeleportAction(
                randomTeleporter,
                randomTeleporter.firstJoinSettings,
                randomTeleporter.firstJoinSettings.landingWorld.getSpawnLocation(),
                true, true,
                randomTeleporter.logRtpOnPlayerJoin, "Rtp-on-join triggered!"
            ).requestLocation();
            event.setSpawnLocation(landingLoc);
            oldSpawnLocationEvents.put(event, landingLoc.clone());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerSpawnLocationMonitor(PlayerSpawnLocationEvent event) {
        Location properLoc = oldSpawnLocationEvents.get(event);
        Location actualLoc = event.getSpawnLocation();
        if (properLoc == null || locIntEqual(properLoc, actualLoc)) return;
        handlerLogging(event.getHandlers().getRegisteredListeners(), "rtp-on-join", "PlayerSpawnLocationEvent");
    }


    /**
     * When {@code onDeathRtp} is enabled, this will RTP a player when they die (with a few exceptions).<p>
     * A player will not be teleported randomly, even if {@code onDeathRtp} is true, if at least one of these
     * conditions is true:<p>
     * • {@code onDeathRequirePermission} is true, and the player does not have the correct permission<p>
     * • {@code onDeathRespectBeds} is true, and the player has a home bed
     *
     * @param event The PlayerRespawnEvent
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void playerRespawn(PlayerRespawnEvent event) {
        //All conditions must be met to continue
        if (randomTeleporter.onDeathRtp &&
            (!randomTeleporter.onDeathRequirePermission || event.getPlayer().hasPermission("jakesrtp.rtpondeath")) &&
            (!randomTeleporter.onDeathRespectBeds || !event.isBedSpawn()) &&
            (!randomTeleporter.onDeathRespectAnchors || !isAnchorSpawn(event))
        ) try {
            Location landingLoc = new RandomTeleportAction(
                randomTeleporter,
                randomTeleporter.onDeathSettings,
                event.getPlayer().getLocation(),
                true, true,
                randomTeleporter.logRtpOnRespawn, "Rtp-on-respawn triggered!"
            ).requestLocation();
            event.setRespawnLocation(landingLoc);
            oldRespawnEvents.put(event, landingLoc.clone());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerRespawnMonitor(PlayerRespawnEvent event) {
        Location properLoc = oldRespawnEvents.get(event);
        Location actualLoc = event.getRespawnLocation();
        if (properLoc == null || locIntEqual(properLoc, actualLoc)) return;
        handlerLogging(event.getHandlers().getRegisteredListeners(), "rtp-on-death", "PlayerRespawnEvent");
    }


    private static void handlerLogging(RegisteredListener[] registeredListeners, String settingName, String eventName) {
        int i = 0, us = 0;
        JakesRtpPlugin.log(Level.WARNING, String.format(
            "It looks like you have %s enabled, but some plugin is messing with the results!\n" +
            "Here is a print out of every plugin that messes with the %s event, hopefully this helps.",
            settingName, eventName));
        for (RegisteredListener listener : registeredListeners) {
            String logMsg = String.format("%02d: [%s] %s", i++,
                                          listener.getPriority().toString(),
                                          listener.getPlugin().getName());
            if (listener.getPlugin() instanceof JakesRtpPlugin) {
                if (us == 0) {
                    logMsg += " ~ Look between here ↓";
                    us++;
                } else if (us == 1) {
                    logMsg += " ~ And here ↑";
                    us++;
                }
            }
            JakesRtpPlugin.infoLog(logMsg);
        }
    }

    private static boolean locIntEqual(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() &&
               a.getBlockY() == b.getBlockY() &&
               a.getBlockZ() == b.getBlockZ();
    }
}
