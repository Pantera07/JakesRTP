package biz.donvi.jakesRTP;

import io.papermc.lib.PaperLib;
import org.bukkit.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static biz.donvi.jakesRTP.SafeLocationUtils.locMatFromSnapshot;

public class SafeLocationFinderOtherThread extends SafeLocationFinder {

    private final Map<String, ChunkSnapshot> chunkSnapshotMap = new HashMap<>();
    private final int timeout;

    /**
     * Just constructs the {@code SafeLocationFinder}, use {@code checkSafety} to check if
     * the current location is safe, and use {@code nextInSpiral} to move on to the next location.
     *
     * @param loc The location that will be checked for safety, and potentially modified.
     */
    public SafeLocationFinderOtherThread(Location loc) {
        super(loc);
        timeout = 5;
    }

    /**
     * This constructs a fully managed {@code SafeLocationFinder}. Because the bounds for the check operations are
     * supplied, this object can check the safety of the location itself by calling instance method
     * {@code tryAndMakeSafe()}. The given location <b>will</b> be modified.
     *
     * @param loc             The location to try and make safe. This <b>will</b> be modified.
     * @param checkRadiusXZ   The distance out from the center that the location cam move.
     * @param checkRadiusVert The distance up and down that the location can move.
     * @param lowBound        The lowest Y value the location can have.
     * @param timeout         The max number of seconds to wait for data from another thread
     */
    public SafeLocationFinderOtherThread(Location loc,
                                         int checkRadiusXZ, int checkRadiusVert, int lowBound, int timeout) {
        super(loc, checkRadiusXZ, checkRadiusVert, lowBound);
        this.timeout = timeout;
    }

    /**
     * Gets the material of the location as if by {@code loc.getBlock().getType()}.<p>
     * Since this is the overridden version, we can not get the material the easy way.
     * Instead, we need to get and use a {@code ChunkSnapshot}.
     *
     * @param loc The location to get the material for.
     */
    @Override
    protected Material getLocMaterial(Location loc) {
        try {
            return locMatFromSnapshot(loc, getChunkForLocation(loc));
        } catch (TimeoutException timeoutException) {
            //Not passing the exception up further because: 1. Its difficult; 2. It reasonably should not be caught here
            System.out.println("Request for chunk snapshot timed out.");
            return Material.CAVE_AIR;
        }
    }

    @Override
    protected void dropToGround() throws Exception {
        try {
            SafeLocationUtils.dropToGround(loc, lowBound, getChunkForLocation(loc));
        } catch (TimeoutException timeoutException) {
            System.out.println("Request for chunk snapshot timed out.");
            throw timeoutException;
        }
    }

    private ChunkSnapshot getChunkForLocation(Location loc) throws TimeoutException {
        String chunkKey = loc.getBlockX() + " " + loc.getBlockZ();
        ChunkSnapshot chunkSnapshot = chunkSnapshotMap.get(chunkKey);
        try {
            chunkSnapshot = Bukkit.getScheduler().callSyncMethod(
                    PluginMain.plugin,
                    new Callable<CompletableFuture<ChunkSnapshot>>() {
                        @Override
                        public CompletableFuture<ChunkSnapshot> call() {
                            return PaperLib.getChunkAtAsync(loc).thenApply(Chunk::getChunkSnapshot);
                        }
                    }
            ).get(timeout, TimeUnit.SECONDS).get(timeout, TimeUnit.SECONDS);
            chunkSnapshotMap.put(chunkKey, chunkSnapshot);
        } catch (InterruptedException e) {
            System.out.println("Caught an unexpected interrupt.");
        } catch (ExecutionException e) {
            e.printStackTrace();

        }
        return chunkSnapshot;
    }

}