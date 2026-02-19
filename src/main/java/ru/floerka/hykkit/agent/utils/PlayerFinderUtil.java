package ru.floerka.hykkit.agent.utils;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.List;
import java.util.UUID;

public class PlayerFinderUtil {

    public static UUID findUUIDByName(String username) {
        Universe universe = Universe.get();
        List<PlayerRef> playerRefs = universe.getPlayers();
        for (PlayerRef playerRef : playerRefs) {
            if(playerRef.getUsername().equals(username)) {
                return playerRef.getUuid();
            }
        }
        return null;
    }
}
