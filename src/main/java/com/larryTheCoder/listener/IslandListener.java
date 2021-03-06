/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2016-2018 larryTheCoder and contributors
 *
 * Permission is hereby granted to any persons and/or organizations
 * using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or
 * any derivatives of the work for commercial use or any other means to generate
 * income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing
 * and/or trademarking this software without explicit permission from larryTheCoder.
 *
 * Any persons and/or organizations using this software must disclose their
 * source code and have it publicly available, include this license,
 * provide sufficient credit to the original authors of the project (IE: larryTheCoder),
 * as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,FITNESS FOR A PARTICULAR
 * PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.larryTheCoder.listener;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockLava;
import cn.nukkit.entity.item.EntityPrimedTNT;
import cn.nukkit.entity.item.EntityVehicle;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.level.Location;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.TextFormat;
import com.larryTheCoder.ASkyBlock;
import com.larryTheCoder.events.IslandEnterEvent;
import com.larryTheCoder.events.IslandExitEvent;
import com.larryTheCoder.storage.IslandData;
import com.larryTheCoder.storage.SettingsFlag;
import com.larryTheCoder.utils.Settings;
import com.larryTheCoder.utils.Utils;

import java.util.List;

import static cn.nukkit.block.BlockID.ENDER_CHEST;

/**
 * Rewrite class for IslandGuard messy code
 * Timestamp: 10:57 AM 6/11/2018
 *
 * @author larryTheCoder
 */
public class IslandListener implements Listener {

    private final ASkyBlock plugin;
    private final MainLogger deb = Server.getInstance().getLogger();

    public IslandListener(ASkyBlock plugin) {
        this.plugin = plugin;
    }

    /**
     * Determines if a location is in the island world or not or in the new
     * nether if it is activated
     *
     * @param loc Location of the entity to be checked
     * @return true if in the island world
     */
    private boolean notInWorld(Location loc) {
        return !ASkyBlock.get().getLevels().contains(loc.getLevel().getName());
    }

    /**
     * Action allowed in this location
     *
     * @param location The location to be checked
     * @param flag     Kind of flag to be checked
     * @return true if allowed
     */
    private boolean actionAllowed(Location location, SettingsFlag flag) {
        IslandData island = plugin.getGrid().getProtectedIslandAt(location);
        if (island != null && island.getIgsSettings().getIgsFlag(flag)) {
            deb.debug("DEBUG: Action is allowed by settings");
            return true;
        }
        deb.debug("DEBUG: Action is defined by settings");
        return Settings.defaultWorldSettings.get(flag);
    }

    /**
     * Checks if action is allowed for player in location for flag
     *
     * @param player   The player or entity
     * @param location The location to be checked
     * @return true if allowed
     */
    private boolean actionAllowed(Player player, Location location, SettingsFlag flag) {
        if (player == null) {
            deb.debug("DEBUG: Checking by default");
            return actionAllowed(location, flag);
        }
        // This permission bypasses protection
        if (player.isOp() || player.hasPermission("is.mod.bypassprotect")) {
            deb.debug("DEBUG: Player has permission to bypass");
            return true;
        }
        IslandData island = plugin.getGrid().getProtectedIslandAt(location);
        if (island != null && (island.getIgsSettings().getIgsFlag(flag) || island.getMembers().contains(player.getName()))) {
            deb.debug("DEBUG: Action is allowed, flag=" + island.getIgsSettings().getIgsFlag(flag) + " member=" + island.getMembers().contains(player.getName()));
            return true;
        }

        if (island == null || island.getOwner() == null) {
            deb.debug("DEBUG: Action not allowed: actionAllowed() check");
            return false;
        }

        if (island.getOwner().equalsIgnoreCase(player.getName())) {
            deb.debug("DEBUG: Action is allowed: actionAllowed() check");
            return true;
        }

        deb.debug("DEBUG: Action is defined by settings");
        // Fixed
        return Settings.defaultWorldSettings.get(flag);
    }

    private String getPrefix() {
        return plugin.getPrefix();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!p.isAlive()) {
            return;
        }
        if (notInWorld(p)) {
            return;
        }
        if (plugin.getGrid() == null) {
            return;
        }
        // Only do something if there is a definite x or z movement
        if (e.getTo().getFloorX() - e.getFrom().getFloorX() == 0 && e.getTo().getFloorZ() - e.getFrom().getFloorZ() == 0) {
            return;
        }
        if (e.getPlayer().isOp() && e.getPlayer().hasPermission("is.mod.bypassprotect")) {
            return;
        }

        final IslandData islandTo = plugin.getGrid().getProtectedIslandAt(e.getTo());
        final IslandData islandFrom = plugin.getGrid().getProtectedIslandAt(e.getFrom()); // Announcement entering
        /*
         * Only says something if there is a change in islands
         *
         * Situations:
         * islandTo == null && islandFrom != null - exit
         * islandTo == null && islandFrom == null - nothing
         * islandTo != null && islandFrom == null - enter
         * islandTo != null && islandFrom != null - same PlayerIsland or teleport?
         * islandTo == islandFrom
         */

        if (islandTo != null && islandFrom == null && (islandTo.getOwner() != null || islandTo.isSpawn())) {
            // Entering
            if (islandTo.isLocked()) {
                p.sendMessage(plugin.getPrefix() + TextFormat.RED + "This island is locked");
                // TODO: Safer way to back player off
                return;
            }

            if (islandTo.getIgsSettings().getIgsFlag(SettingsFlag.ENTER_EXIT_MESSAGES)) {
                p.sendMessage(plugin.getPrefix() + TextFormat.GREEN + "Entering " + islandTo.getName() + "'s island");
            }

            // Fire entry event
            final IslandEnterEvent event = new IslandEnterEvent(p, islandTo, e.getTo());
            plugin.getServer().getPluginManager().callEvent(event);
        } else if (islandTo == null && islandFrom != null && (islandFrom.getOwner() != null || islandFrom.isSpawn())) {
            // Leaving
            if (islandFrom.getIgsSettings().getIgsFlag(SettingsFlag.ENTER_EXIT_MESSAGES)) {
                p.sendMessage(plugin.getPrefix() + TextFormat.GREEN + "Leaving " + islandFrom.getName() + "'s island");
            }

            // Fire exit event
            final IslandExitEvent event = new IslandExitEvent(p, islandFrom, e.getFrom());
            plugin.getServer().getPluginManager().callEvent(event);
        } else if (islandTo != null && islandFrom != null && !islandTo.equals(islandFrom)) {
            // Fire exit event
            final IslandExitEvent event = new IslandExitEvent(p, islandFrom, e.getFrom());
            plugin.getServer().getPluginManager().callEvent(event);
            // Fire entry event
            final IslandEnterEvent event2 = new IslandEnterEvent(p, islandTo, e.getTo());
            plugin.getServer().getPluginManager().callEvent(event2);
        } else if (islandTo != null && (islandTo.getOwner() != null || islandTo.isSpawn())) {
            // Lock check
            if (islandTo.isLocked()) {
                if (!p.isOp() && !p.hasPermission("is.mod.bypassprotect") && !p.hasPermission("is.mod.bypasslock")) {
                    // Dismount
                    ((EntityVehicle) p.riding).mountEntity(p);
                    e.setCancelled();
                    // TODO: Find a best way to back off the player from the locked island
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketUseEvent(PlayerBucketFillEvent e) {
        deb.debug("DEBUG: " + e.getEventName());
        Player p = e.getPlayer();
        if (notInWorld(p)) {
            deb.debug("Event is not in world");
            return;
        }
        if (actionAllowed(p, e.getBlockClicked().getLocation(), SettingsFlag.BUCKET)) {
            deb.debug("Event, use bucket is allowed");
            if (e.getBlockClicked() instanceof BlockLava && actionAllowed(p, e.getBlockClicked().getLocation(), SettingsFlag.COLLECT_LAVA)) {
                deb.debug("Event is using Lava and its allowed");
                return;
            } else if (actionAllowed(p, e.getBlockClicked().getLocation(), SettingsFlag.COLLECT_WATER)) {
                deb.debug("Event is using Water and its allowed");
                return;
            }
        }
        deb.debug("Event is not allowed");
        e.setCancelled();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        deb.debug("DEBUG: " + e.getEventName());
        Player p = e.getPlayer();
        if (notInWorld(p)) {
            deb.debug("Event is not in world");
            return;
        }
        if (actionAllowed(p, e.getBlockClicked().getLocation(), SettingsFlag.BUCKET)) {
            deb.debug("Event, use bucket is allowed");
            if (e.getBlockClicked() instanceof BlockLava && actionAllowed(p, e.getBlockClicked().getLocation(), SettingsFlag.COLLECT_LAVA)) {
                deb.debug("Event is using Lava and its allowed");
                return;
            } else if (actionAllowed(p, e.getBlockClicked().getLocation(), SettingsFlag.COLLECT_WATER)) {
                deb.debug("Event is using Water and its allowed");
                return;
            }
        }
        deb.debug("Event is not allowed");
        e.setCancelled();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        deb.debug("DEBUG: " + e.getEventName());
        deb.debug("DEBUG: Item is " + e.getItem().toString());
        Player p = e.getPlayer();
        if (notInWorld(p)) {
            deb.debug("Event is not in world");
            return;
        }
        if (p.isOp() || p.hasPermission("is.mod.bypassprotect")) {
            return;
        }
        // Too bad that the item is not a vector3
        if (plugin.getIsland().locationIsOnIsland(p, e.getPlayer())) {
            deb.debug("Action is allowed: Player on island");
            // You can do anything on your island
            return;
        }
        if (actionAllowed(e.getPlayer().getLocation(), SettingsFlag.VISITOR_ITEM_DROP)) {
            deb.debug("Action is allowed: Drop item is allowed");
            return;
        }

        // Do not send any message, it may spam
        e.setCancelled();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent e) {
        deb.debug("DEBUG: " + e.getEventName());
        deb.debug("DEBUG: Block is " + e.getBlock().getName());
        Player p = e.getPlayer();
        if (notInWorld(p)) {
            deb.debug("Event is not in world");
            return;
        }
        if (p.isOp() || p.hasPermission("is.mod.bypassprotect")) {
            return;
        }
        if (plugin.getIsland().locationIsOnIsland(p, e.getBlock()) || plugin.getIsland().locationIsOnIsland(p, p.getLocation())) {
            deb.debug("Action is allowed: Player on island");
            // You can do anything on your island
            return;
        }
        // Player is not clicking a block, they are clicking a material so this
        // is driven by where the player is
        if (e.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK && e.getBlock() == null) {
            deb.debug("Action is allowed: Use clicking air");
            return;
        }

        switch (e.getAction()) {
            case PHYSICAL:
                // Only 2 of them check either its a Pressure Plate or Farmlands
                if (!Utils.actionPhysical(p, e.getBlock())) {
                    // No need stupid checks. Just cancel them
                    p.sendMessage(getPrefix() + plugin.getLocale(e.getPlayer()).islandProtected);
                    e.setCancelled();
                    deb.debug("Action is blocked");
                    return;
                }
                break;
            case LEFT_CLICK_BLOCK:
                // Player is interacting with an item. Check if it allowed
                if (!Utils.isItemAllowed(p, e.getItem())) {
                    // No need stupid checks. Just cancel them
                    p.sendMessage(getPrefix() + plugin.getLocale(e.getPlayer()).islandProtected);
                    e.setCancelled();
                    deb.debug("Action is blocked");
                    return;
                }
                break;
            case RIGHT_CLICK_BLOCK:
                // Player is clicking on something. Check if it allowed
                if (!Utils.isInventoryAllowed(p, e.getBlock())) {
                    // No need stupid checks. Just cancel them
                    p.sendMessage(getPrefix() + plugin.getLocale(e.getPlayer()).islandProtected);
                    e.setCancelled();
                    deb.debug("Action is blocked");
                    return;
                }
                break;
        }

        deb.debug("Action is allowed: Settings defined");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent e) {
        if (notInWorld(e.getPosition().getLocation())) {
            return;
        }
        if (e.getEntity() instanceof EntityPrimedTNT && ((EntityPrimedTNT) e.getEntity()).getSource() instanceof Player) {
            Player p = (Player) ((EntityPrimedTNT) e.getEntity()).getSource();
            if (actionAllowed(p, e.getEntity().getLocation(), SettingsFlag.BREAK_BLOCKS)) {
                return;
            }
            if (p.isOp() && p.hasPermission("is.mod.bypassprotect")) {
                return;
            }
        }


        // As what I said, no need stupid checks
        e.getBlockList().clear();
        e.setCancelled();
    }

    /**
     * This method protects players from PVP if it is not allowed and from
     * arrows fired by other players
     *
     * @param e Event
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        // The damager is in the world but the entity who got attacked it is not? Oh no
        if (notInWorld(e.getDamager().getLocation()) || notInWorld(e.getEntity())) {
            return;
        }
        // TODO: A subject
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        deb.debug("DEBUG: " + e.getEventName());
        deb.debug("DEBUG: Block is " + e.getBlock().toString());
        // Check if the player in the world
        if (notInWorld(e.getPlayer())) {
            deb.debug("Event is not in world");
            return;
        }

        if (actionAllowed(e.getPlayer(), e.getBlock().getLocation(), SettingsFlag.PLACE_BLOCKS)) {
            deb.debug("Action is allowed");
            return;
        }

        // Cancel them, obviously
        e.getPlayer().sendMessage(plugin.getPrefix() + plugin.getLocale(e.getPlayer()).islandProtected);
        e.setCancelled();
        deb.debug("Action is not allowed and cancelled");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(final BlockBreakEvent e) {
        deb.debug("DEBUG: " + e.getEventName());
        deb.debug("DEBUG: Block is " + e.getBlock().toString());
        if (notInWorld(e.getPlayer())) {
            deb.debug("Event is not in world");
            return;
        }
        if (actionAllowed(e.getPlayer(), e.getBlock().getLocation(), SettingsFlag.BREAK_BLOCKS)) {
            deb.debug("Action is allowed");
            return;
        }

        // Everyone else - not allowed
        e.getPlayer().sendMessage(getPrefix() + plugin.getLocale(e.getPlayer()).islandProtected);
        e.setCancelled();
        deb.debug("Action is not allowed and cancelled");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCraft(CraftItemEvent event) {
        Player player = event.getPlayer();
        if (notInWorld(player)) {
            return;
        }
        if (event.getRecipe().getResult().getId() == ENDER_CHEST && !player.hasPermission("is.craft.enderchest")) {
            player.sendMessage(plugin.getLocale(player).errorNoPermission);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerLogin(PlayerPreLoginEvent ex) {
        Player p = ex.getPlayer();
        plugin.getIslandInfo(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent ex) {
        // load player inventory if exists
        Player p = ex.getPlayer();
        plugin.getInventory().loadPlayerInventory(p);
        // Load player data
        if (plugin.getPlayerInfo(p) == null) {
            Utils.send(p.getName() + "&a data doesn't exists. Creating new ones");
            plugin.getDatabase().createPlayer(p.getName());
        }

        // Load messages
        List<String> news = plugin.getMessages().getMessages(p.getName());

        if (news != null && news.isEmpty()) {
            p.sendMessage(plugin.getLocale(p).newNews.replace("[count]", Integer.toString(news.size())));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerLeave(PlayerQuitEvent ex) {
        Player p = ex.getPlayer();
        IslandData pd = plugin.getIslandInfo(p);
        if (pd != null) {
            // Remove the island data from cache provides the memory to server
            plugin.getDatabase().removeIslandFromCache(pd);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent ex) {
        // todo: Block player messages.
    }
}
