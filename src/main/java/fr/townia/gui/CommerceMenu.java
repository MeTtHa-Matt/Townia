package fr.townia.gui;

import fr.townia.TowniaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommerceMenu implements Listener {
    private static final int SIDE_SIZE = 27;
    private static final int LEFT_START = 0;
    private static final int RIGHT_START = 27;
    private static final int CANCEL_SLOT = 45;
    private static final int VALIDATE_SLOT = 53;

    private final TowniaPlugin plugin;
    private final Map<UUID, TradeRequest> pendingRequests = new HashMap<>();
    private final Map<UUID, TradeSession> activeSessions = new HashMap<>();
    private final Map<UUID, Long> lastRequestAt = new HashMap<>();

    public CommerceMenu(TowniaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean request(Player requester, Player target) {
        if (requester == null || target == null) return false;
        if (requester.equals(target)) {
            requester.sendMessage(ChatColor.RED + "Tu ne peux pas te proposer un commerce à toi-même.");
            return true;
        }

        long now = System.currentTimeMillis();
        Long last = lastRequestAt.get(requester.getUniqueId());
        if (last != null && now - last < 3000L) {
            requester.sendMessage(ChatColor.RED + "Merci d'attendre un peu avant de refaire une demande de commerce.");
            return true;
        }
        lastRequestAt.put(requester.getUniqueId(), now);

        if (activeSessions.containsKey(requester.getUniqueId()) || activeSessions.containsKey(target.getUniqueId())) {
            requester.sendMessage(ChatColor.YELLOW + "Une transaction de commerce est déjà active avec ce joueur.");
            return true;
        }

        pendingRequests.put(target.getUniqueId(), new TradeRequest(requester.getUniqueId(), target.getUniqueId(), now));
        requester.sendMessage(ChatColor.GREEN + "Demande de commerce envoyée à " + target.getName() + ".");
        target.sendMessage(ChatColor.GOLD + requester.getName() + ChatColor.WHITE + " souhaite commercer avec toi."
            + ChatColor.YELLOW + " Tape /commerce accept" + ChatColor.WHITE + " ou " + ChatColor.RED + "/commerce refuse");
        return true;
    }

    public boolean accept(Player player) {
        if (player == null) return false;
        TradeRequest request = pendingRequests.remove(player.getUniqueId());
        if (request == null) {
            player.sendMessage(ChatColor.RED + "Tu n'as aucune demande de commerce en attente.");
            return true;
        }

        Player requester = Bukkit.getPlayer(request.requesterId);
        if (requester == null || !requester.isOnline()) {
            player.sendMessage(ChatColor.RED + "Le joueur qui a demandé le commerce n'est plus connecté.");
            return true;
        }

        if (activeSessions.containsKey(player.getUniqueId()) || activeSessions.containsKey(requester.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Une transaction est déjà en cours avec ce joueur.");
            return true;
        }

        TradeSession session = new TradeSession(requester.getUniqueId(), player.getUniqueId());
        activeSessions.put(requester.getUniqueId(), session);
        activeSessions.put(player.getUniqueId(), session);

        player.sendMessage(ChatColor.GREEN + "Commerce accepté avec " + requester.getName() + ".");
        requester.sendMessage(ChatColor.GREEN + player.getName() + " a accepté le commerce.");

        openTrade(player, session);
        openTrade(requester, session);
        return true;
    }

    public boolean refuse(Player player) {
        if (player == null) return false;
        TradeRequest request = pendingRequests.remove(player.getUniqueId());
        if (request == null) {
            player.sendMessage(ChatColor.RED + "Aucune demande de commerce en attente.");
            return true;
        }
        Player requester = Bukkit.getPlayer(request.requesterId);
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(ChatColor.RED + player.getName() + " a refusé le commerce.");
        }
        player.sendMessage(ChatColor.YELLOW + "Demande de commerce refusée.");
        return true;
    }

    public void cancelTrade(Player player) {
        TradeSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) return;

        UUID otherId = session.other(player.getUniqueId());
        activeSessions.remove(otherId);

        refundOffer(player, session.offerFor(player.getUniqueId()));
        if (otherId != null) {
            Player other = Bukkit.getPlayer(otherId);
            if (other != null && other.isOnline()) {
                refundOffer(other, session.offerFor(otherId));
                other.closeInventory();
                other.sendMessage(ChatColor.RED + "Le commerce a été annulé.");
            }
        }
        player.closeInventory();
        player.sendMessage(ChatColor.RED + "Le commerce a été annulé.");
    }

    private void refundOffer(Player player, ItemStack[] offer) {
        if (player == null || offer == null) return;
        for (ItemStack item : offer) {
            if (item == null || item.getType() == Material.AIR) continue;
            player.getInventory().addItem(item.clone());
        }
        Arrays.fill(offer, null);
    }

    private void openTrade(Player player, TradeSession session) {
        if (player == null || !player.isOnline()) return;
        Inventory inventory = Bukkit.createInventory(null, 54, "Commerce : " + session.otherName(player.getUniqueId()));
        fillTradeBackground(inventory, session, player);
        player.openInventory(inventory);
    }

    private void fillTradeBackground(Inventory inventory, TradeSession session, Player viewer) {
        Player other = Bukkit.getPlayer(session.other(viewer.getUniqueId()));
        String otherName = other == null ? "Joueur" : other.getName();

        for (int i = 0; i < 54; i++) {
            if (i == CANCEL_SLOT || i == VALIDATE_SLOT) continue;
            if (i >= LEFT_START && i < LEFT_START + SIDE_SIZE) {
                inventory.setItem(i, pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, ChatColor.AQUA + "Votre offre"));
            } else if (i >= RIGHT_START && i < RIGHT_START + SIDE_SIZE) {
                inventory.setItem(i, pane(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GOLD + "Offre de " + otherName));
            } else {
                inventory.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE, ""));
            }
        }

        inventory.setItem(CANCEL_SLOT, actionPane(Material.RED_WOOL, ChatColor.RED + "Annuler la transaction"));
        inventory.setItem(VALIDATE_SLOT, actionPane(Material.LIME_WOOL, ChatColor.GREEN + "Valider la transaction"));

        ItemStack[] ownOffer = session.offerFor(viewer.getUniqueId());
        ItemStack[] otherOffer = session.offerFor(session.other(viewer.getUniqueId()));

        for (int slot = 0; slot < SIDE_SIZE; slot++) {
            if (ownOffer[slot] != null && ownOffer[slot].getType() != Material.AIR) {
                inventory.setItem(LEFT_START + slot, ownOffer[slot].clone());
            }
            if (otherOffer[slot] != null && otherOffer[slot].getType() != Material.AIR) {
                inventory.setItem(RIGHT_START + slot, otherOffer[slot].clone());
            }
        }
    }

    private ItemStack pane(Material material, String title) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack actionPane(Material material, String title) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Cliquez pour confirmer");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        if (inventory == null || inventory.getSize() != 54 || !event.getView().getTitle().startsWith("Commerce : ")) return;
        event.setCancelled(true);

        TradeSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (event.getClickedInventory() == player.getInventory()) {
            handleInventoryToTrade(player, session, event.getSlot(), event.getClick());
            refreshTrade(player, session);
            Player other = Bukkit.getPlayer(session.other(player.getUniqueId()));
            refreshTrade(other, session);
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;
        if (slot == CANCEL_SLOT) {
            cancelTrade(player);
            return;
        }
        if (slot == VALIDATE_SLOT) {
            validateTrade(player, session);
            return;
        }

        if (slot >= LEFT_START && slot < LEFT_START + SIDE_SIZE) {
            handleTradeSlotInteraction(player, session, slot, event.getClick(), true);
            refreshTrade(player, session);
            Player other = Bukkit.getPlayer(session.other(player.getUniqueId()));
            refreshTrade(other, session);
            return;
        }

        if (slot >= RIGHT_START && slot < RIGHT_START + SIDE_SIZE) {
            handleTradeSlotInteraction(player, session, slot - RIGHT_START, event.getClick(), false);
            refreshTrade(player, session);
            Player other = Bukkit.getPlayer(session.other(player.getUniqueId()));
            refreshTrade(other, session);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith("Commerce : ")) return;

        TradeSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        InventoryCloseEvent.Reason reason = event.getReason();
        if (reason == InventoryCloseEvent.Reason.PLAYER) {
            return;
        }

        cancelTrade(player);
    }

    private void handleInventoryToTrade(Player player, TradeSession session, int inventorySlot, org.bukkit.event.inventory.ClickType clickType) {
        ItemStack source = player.getInventory().getItem(inventorySlot);
        if (source == null || source.getType() == Material.AIR) return;

        ItemStack[] offer = session.offerFor(player.getUniqueId());
        if (clickType == org.bukkit.event.inventory.ClickType.LEFT) {
            addToOffer(offer, source, 1, player.getInventory(), inventorySlot, true);
        } else if (clickType == org.bukkit.event.inventory.ClickType.RIGHT) {
            addToOffer(offer, source, source.getAmount(), player.getInventory(), inventorySlot, true);
        } else if (clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
            moveSimilarItemsToOffer(player, offer, source);
        }
    }

    private void handleTradeSlotInteraction(Player player, TradeSession session, int offerSlot, org.bukkit.event.inventory.ClickType clickType, boolean ownOffer) {
        if (!ownOffer) return;
        ItemStack[] offer = session.offerFor(player.getUniqueId());
        ItemStack item = offer[offerSlot];
        if (item == null || item.getType() == Material.AIR) return;

        if (clickType == org.bukkit.event.inventory.ClickType.LEFT) {
            moveFromOfferToInventory(player, offer, offerSlot, 1);
        } else if (clickType == org.bukkit.event.inventory.ClickType.RIGHT) {
            moveFromOfferToInventory(player, offer, offerSlot, item.getAmount());
        } else if (clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
            moveFromOfferToInventory(player, offer, offerSlot, item.getAmount());
        }
    }

    private void addToOffer(ItemStack[] offer, ItemStack source, int amount, Inventory playerInventory, int inventorySlot, boolean removeFromInventory) {
        if (source == null || source.getType() == Material.AIR) return;
        int toMove = Math.min(amount, source.getAmount());
        if (toMove <= 0) return;

        for (int i = 0; i < offer.length; i++) {
            ItemStack current = offer[i];
            if (current != null && current.getType() != Material.AIR && current.isSimilar(source)) {
                int freeSpace = current.getMaxStackSize() - current.getAmount();
                int toAdd = Math.min(freeSpace, toMove);
                if (toAdd > 0) {
                    current.setAmount(current.getAmount() + toAdd);
                    toMove -= toAdd;
                    if (removeFromInventory) removeFromInventory(playerInventory, inventorySlot, toAdd, source);
                    if (toMove <= 0) return;
                }
            }
        }

        for (int i = 0; i < offer.length && toMove > 0; i++) {
            if (offer[i] == null || offer[i].getType() == Material.AIR) {
                ItemStack clone = source.clone();
                int amountToStore = Math.min(clone.getMaxStackSize(), toMove);
                clone.setAmount(amountToStore);
                offer[i] = clone;
                toMove -= amountToStore;
                if (removeFromInventory) removeFromInventory(playerInventory, inventorySlot, amountToStore, source);
            }
        }
    }

    private void removeFromInventory(Inventory inventory, int slot, int amount, ItemStack sourceTemplate) {
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType() == Material.AIR) return;

        int remaining = stack.getAmount() - amount;
        if (remaining <= 0) {
            inventory.setItem(slot, null);
        } else {
            ItemStack clone = stack.clone();
            clone.setAmount(remaining);
            inventory.setItem(slot, clone);
        }
    }

    private void moveSimilarItemsToOffer(Player player, ItemStack[] offer, ItemStack sourceTemplate) {
        if (sourceTemplate == null || sourceTemplate.getType() == Material.AIR) return;

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (!stack.isSimilar(sourceTemplate)) continue;

            int amount = stack.getAmount();
            addToOffer(offer, stack, amount, player.getInventory(), slot, true);
            if (player.getInventory().getItem(slot) == null || player.getInventory().getItem(slot).getType() == Material.AIR) continue;
        }
    }

    private void moveFromOfferToInventory(Player player, ItemStack[] offer, int offerSlot, int amount) {
        ItemStack item = offer[offerSlot];
        if (item == null || item.getType() == Material.AIR) return;

        int toMove = Math.min(amount, item.getAmount());
        ItemStack returned = item.clone();
        returned.setAmount(toMove);
        player.getInventory().addItem(returned);

        int remaining = item.getAmount() - toMove;
        if (remaining <= 0) {
            offer[offerSlot] = null;
        } else {
            item.setAmount(remaining);
        }
    }

    private void validateTrade(Player player, TradeSession session) {
        Player other = Bukkit.getPlayer(session.other(player.getUniqueId()));
        if (other == null || !other.isOnline()) {
            cancelTrade(player);
            return;
        }

        if (isEmpty(session.offerFor(player.getUniqueId())) && isEmpty(session.offerFor(other.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + "Le commerce ne peut pas être validé si les deux offres sont vides.");
            return;
        }

        for (ItemStack item : session.offerFor(player.getUniqueId())) {
            if (item == null || item.getType() == Material.AIR) continue;
            other.getInventory().addItem(item.clone());
        }
        for (ItemStack item : session.offerFor(other.getUniqueId())) {
            if (item == null || item.getType() == Material.AIR) continue;
            player.getInventory().addItem(item.clone());
        }

        Arrays.fill(session.leftOffer, null);
        Arrays.fill(session.rightOffer, null);
        activeSessions.remove(player.getUniqueId());
        activeSessions.remove(other.getUniqueId());

        player.closeInventory();
        other.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Transaction validée avec succès.");
        other.sendMessage(ChatColor.GREEN + "Transaction validée avec succès.");
    }

    private void refreshTrade(Player player, TradeSession session) {
        if (player == null || !player.isOnline()) return;
        if (!session.isParticipant(player.getUniqueId())) return;
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null
            && player.getOpenInventory().getTopInventory().getSize() == 54
            && player.getOpenInventory().getTitle().startsWith("Commerce : ")) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            fillTradeBackground(inventory, session, player);
        }
    }

    private boolean isEmpty(ItemStack[] offer) {
        for (ItemStack item : offer) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        return true;
    }

    private static final class TradeRequest {
        private final UUID requesterId;
        private final UUID targetId;
        private final long createdAt;

        private TradeRequest(UUID requesterId, UUID targetId, long createdAt) {
            this.requesterId = requesterId;
            this.targetId = targetId;
            this.createdAt = createdAt;
        }
    }

    private static final class TradeSession {
        private final UUID leftId;
        private final UUID rightId;
        private final ItemStack[] leftOffer = new ItemStack[SIDE_SIZE];
        private final ItemStack[] rightOffer = new ItemStack[SIDE_SIZE];

        private TradeSession(UUID leftId, UUID rightId) {
            this.leftId = leftId;
            this.rightId = rightId;
        }

        public UUID other(UUID playerId) {
            if (leftId.equals(playerId)) return rightId;
            if (rightId.equals(playerId)) return leftId;
            return null;
        }

        public String otherName(UUID playerId) {
            UUID otherId = other(playerId);
            if (otherId == null) return "Joueur";
            Player player = Bukkit.getPlayer(otherId);
            return player == null ? "Joueur" : player.getName();
        }

        public boolean isParticipant(UUID playerId) {
            return leftId.equals(playerId) || rightId.equals(playerId);
        }

        public ItemStack[] offerFor(UUID playerId) {
            return leftId.equals(playerId) ? leftOffer : rightOffer;
        }
    }
}
