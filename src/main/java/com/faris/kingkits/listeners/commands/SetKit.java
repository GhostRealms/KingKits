package com.faris.kingkits.listeners.commands;

import com.faris.kingkits.KingKits;
import com.faris.kingkits.Kit;
import com.faris.kingkits.helpers.Utils;
import com.faris.kingkits.listeners.event.custom.PlayerKitEvent;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SetKit {

    public static void setKingKit(Player player, String kitName, boolean sendMessages) throws Exception {
        final Kit kit = setKit(player, sendMessages, kitName);
        if (kit != null) {
            KingKits pl = KingKits.getInstance();
            if (kit.hasCooldown() && !player.hasPermission(pl.permissions.kitBypassCooldown)) {
                final String playerName = player.getName();
                final String newKitName = kit.getRealName();
                pl.getCooldownConfig().set(playerName + "." + newKitName, System.currentTimeMillis());
                pl.saveCooldownConfig();
            }
        }
    }


    public static boolean setKit(Player player, String kitName, boolean sendMessages) throws Exception {
        return setKit(player, sendMessages, kitName) != null;
    }

    @SuppressWarnings("deprecation")
    public static Kit setKit(Player player, boolean sendMessages, String kitName) throws Exception {
        if (player == null || kitName == null) return null;
        KingKits plugin = KingKits.getInstance();
        if (plugin.configValues.pvpWorlds.contains("All") || plugin.configValues.pvpWorlds.contains(player.getWorld().getName())) {
            List<String> kitList = plugin.getConfigKitList();
            List<String> kitListLC = Utils.toLowerCaseList(kitList);
            if (kitListLC.contains(kitName.toLowerCase())) {
                try {
                    kitName = kitList.get(kitList.indexOf(kitName));
                } catch (Exception ex) {
                    try {
                        kitName = kitName.substring(0, 0).toUpperCase() + kitName.substring(1);
                    } catch (Exception ex2) {
                    }
                }
                Kit newKit = plugin.kitList.get(Utils.stripColour(kitName));
                if (newKit == null) return null;
                if (player.hasPermission("kingkits.kits." + newKit.getRealName().toLowerCase())) {
                    if (plugin.configValues.oneKitPerLife) {
                        if (plugin.configValues.opBypass) {
                            if (!player.isOp()) {
                                if (plugin.playerKits.containsKey(player.getName())) {
                                    if (sendMessages) player.sendMessage(r("&6You have already chosen a kit!"));
                                    return null;
                                }
                            }
                        } else {
                            if (plugin.usingKits.containsKey(player.getName())) {
                                if (sendMessages) player.sendMessage(r("&6You have already chosen a kit!"));
                                return null;
                            }
                        }
                    }
                    String oldKit = plugin.playerKits.containsKey(player.getName()) ? plugin.playerKits.get(player.getName()) : "";
                    PlayerKitEvent playerKitEvent = new PlayerKitEvent(player, kitName, oldKit, newKit.getItemsWithSlot(), newKit.getArmour(), newKit.getPotionEffects());
                    playerKitEvent.setCommands(newKit.getCommands());
                    player.getServer().getPluginManager().callEvent(playerKitEvent);
                    if (!playerKitEvent.isCancelled()) {
                        if (plugin.configValues.vaultValues.useEconomy && plugin.configValues.vaultValues.useCostPerKit) {
                            try {
                                net.milkbowl.vault.economy.Economy economy = (net.milkbowl.vault.economy.Economy) plugin.vault.getEconomy();
                                double kitCost = newKit.getCost();
                                if (kitCost < 0) kitCost *= -1;
                                if (economy.hasAccount(player.getName())) {
                                    if (economy.getBalance(player.getName()) >= kitCost) {
                                        economy.withdrawPlayer(player.getName(), kitCost);
                                        if (kitCost != 0)
                                            player.sendMessage(ChatColor.GREEN + plugin.getEconomyMessage(kitCost));
                                    } else {
                                        if (sendMessages)
                                            player.sendMessage(ChatColor.GREEN + "You do not have enough money to change kits.");
                                        return null;
                                    }
                                } else {
                                    if (sendMessages)
                                        player.sendMessage(ChatColor.GREEN + "You do not have enough money to change kits.");
                                    return null;
                                }
                            } catch (Exception ex) {
                            }
                        }

                        if (plugin.configValues.replaceItems) {
                            player.getInventory().clear();
                            player.getInventory().setArmorContents(null);
                        }
                        player.setGameMode(GameMode.SURVIVAL);
                        for (PotionEffect potionEffect : player.getActivePotionEffects())
                            player.removePotionEffect(potionEffect.getType());
                        if (plugin.configValues.replaceItems) {
                            Map<Integer, ItemStack> kitItems = playerKitEvent.getKitContentsWithSlots();
                            for (Map.Entry<Integer, ItemStack> kitItem : kitItems.entrySet()) {
                                try {
                                    if (kitItem.getValue() != null && kitItem.getValue().getType() != Material.AIR) {
                                        int slot = kitItem.getKey();
                                        if (slot >= 0 && slot < player.getInventory().getSize()) {
                                            player.getInventory().setItem(slot, kitItem.getValue());
                                        }
                                    }
                                } catch (Exception ex) {
                                    continue;
                                }
                            }
                        } else {
                            List<ItemStack> kitItems = playerKitEvent.getKitContents();
                            for (int i = 0; i < kitItems.size(); i++) {
                                try {
                                    ItemStack kitItem = kitItems.get(i);
                                    if (kitItem != null && kitItem.getType() != Material.AIR)
                                        player.getInventory().addItem(kitItem);
                                } catch (Exception ex) {
                                    continue;
                                }
                            }
                        }
                        List<ItemStack> armourItems = playerKitEvent.getKitArmour();
                        List<ItemStack> leftOverArmour = new ArrayList<ItemStack>();
                        for (ItemStack armourItem : armourItems) {
                            if (armourItem != null) {
                                String strArmourType = armourItem.getType().toString().toLowerCase();
                                if (strArmourType.endsWith("helmet") && (plugin.configValues.replaceItems ? true : Utils.isItemNull(player.getInventory().getHelmet())))
                                    player.getInventory().setHelmet(armourItem);
                                else if (strArmourType.endsWith("chestplate") && (plugin.configValues.replaceItems ? true : Utils.isItemNull(player.getInventory().getChestplate())))
                                    player.getInventory().setChestplate(armourItem);
                                else if ((strArmourType.endsWith("leggings") || strArmourType.endsWith("pants")) && (plugin.configValues.replaceItems ? true : Utils.isItemNull(player.getInventory().getLeggings())))
                                    player.getInventory().setLeggings(armourItem);
                                else if (strArmourType.endsWith("boots") && (plugin.configValues.replaceItems ? true : Utils.isItemNull(player.getInventory().getBoots())))
                                    player.getInventory().setBoots(armourItem);
                                else if ((plugin.configValues.replaceItems || (!strArmourType.endsWith("helmet") && !strArmourType.endsWith("chestplate") && !strArmourType.endsWith("leggings") && !strArmourType.endsWith("pants") && !strArmourType.endsWith("boots"))) && player.getInventory().getHelmet() == null)
                                    leftOverArmour.add(armourItem);
                                else player.getInventory().addItem(armourItem);
                            }
                        }
                        if (!leftOverArmour.isEmpty()) {
                            player.getInventory().setHelmet(leftOverArmour.get(0));
                            for (int i = 1; i < leftOverArmour.size(); i++)
                                player.getInventory().addItem(leftOverArmour.get(i));
                        }
                        player.updateInventory();
                        player.addPotionEffects(playerKitEvent.getPotionEffects());

                        if (plugin.configValues.commandToRun.length() > 0) {
                            String cmdToRun = plugin.configValues.commandToRun;
                            cmdToRun = cmdToRun.replace("<kit>", kitName);
                            cmdToRun = cmdToRun.replace("<player>", player.getName()).replace("<displayname>", player.getDisplayName());
                            player.getServer().dispatchCommand(player.getServer().getConsoleSender(), cmdToRun);
                        }
                        for (String cmdToRun : playerKitEvent.getCommands()) {
                            cmdToRun = cmdToRun.replace("<player>", player.getName()).replace("<displayname>", player.getDisplayName());
                            player.getServer().dispatchCommand(player.getServer().getConsoleSender(), cmdToRun);
                        }
                        plugin.playerKits.remove(player.getName());
                        plugin.usingKits.remove(player.getName());
                        if (plugin.configValues.opBypass) {
                            if (!player.isOp()) {
                                plugin.playerKits.put(player.getName(), newKit.getRealName());
                            }
                        } else {
                            plugin.playerKits.put(player.getName(), newKit.getRealName());
                        }
                        plugin.usingKits.put(player.getName(), newKit.getRealName());
                        if (plugin.configValues.customMessages != "" && plugin.configValues.customMessages != "''")
                            player.sendMessage(r(plugin.configValues.customMessages).replace("<player>", player.getName()).replace("<displayname>", player.getDisplayName()).replace("<kit>", kitName));
                        if (plugin.configValues.kitParticleEffects) {
                            player.playEffect(player.getLocation().add(0, 1, 0), Effect.ENDER_SIGNAL, (byte) 0);
                        }
                        return newKit;
                    } else {
                        for (PotionEffect potionEffect : player.getActivePotionEffects())
                            player.removePotionEffect(potionEffect.getType());
                    }
                } else {
                    if (sendMessages)
                        player.sendMessage(r("&cYou do not have permission to use the kit &4" + kitName + "&c."));
                }
            } else {
                if (sendMessages) player.sendMessage(r("&4" + kitName + " &6does not exist."));
            }
        }
        return null;
    }

    private static String r(String val) {
        return Utils.replaceChatColour(val);
    }

}
