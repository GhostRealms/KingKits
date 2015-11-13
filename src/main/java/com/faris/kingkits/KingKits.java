package com.faris.kingkits;

import com.faris.easysql.mysql.MySQLDetails;
import com.faris.kingkits.controller.*;
import com.faris.kingkits.helper.util.BukkitUtilities;
import com.faris.kingkits.helper.util.ObjectUtilities;
import com.faris.kingkits.helper.util.PlayerUtilities;
import com.faris.kingkits.helper.util.Utilities;
import com.faris.kingkits.listener.CommandListener;
import com.faris.kingkits.listener.EventListener;
import com.faris.kingkits.listener.commands.*;
import com.faris.kingkits.player.KitPlayer;
import com.faris.kingkits.player.OfflineKitPlayer;
import com.faris.kingkits.storage.DataStorage;
import com.faris.kingkits.storage.FlatFileStorage;
import com.faris.kingkits.updater.BukkitUpdater;
import com.faris.kingkits.updater.SpigotUpdater;
import nl.arfie.bukkit.attributes.Attribute;
import org.bukkit.*;
import org.bukkit.configuration.serialization.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.*;
import org.bukkit.potion.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class KingKits extends JavaPlugin {

	private EventListener eventListener = null;

	private int autoSaveTaskID = -1;
	private boolean allowJoining = true;

	@Override
	public void onEnable() {
		ConfigurationSerialization.registerClass(Kit.class);
		ConfigurationSerialization.registerClass(OfflineKitPlayer.class);
		ConfigurationSerialization.registerClass(Attribute.class);
		ConfigurationSerialization.registerClass(MySQLDetails.class);

		if (new File(this.getDataFolder(), "config.yml").exists() && !ConfigController.getInstance().getConfig().contains("Version"))
			ConfigController.getInstance().migrateOldConfigs();
		else ConfigController.getInstance().loadConfiguration();
		try {
			Messages.initMessages(this);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		SQLController.getInstance();
		KitController.getInstance().loadKits();
		PlayerController.getInstance();
		GuiController.getInstance().loadInventories();
		CompassController.getInstance();

		try {
			if (Messages.KIT_NOT_ENOUGH_MONEY.getRawMessage().contains("%d"))
				Messages.KIT_NOT_ENOUGH_MONEY.setMessage(Messages.KIT_NOT_ENOUGH_MONEY.getRawMessage().replace("%d", "%.2f"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		this.getServer().getPluginManager().registerEvents((this.eventListener = new EventListener(this)), this);

		if (ConfigController.getInstance().shouldCheckForUpdates()) {
			this.getLogger().info("Checking for updates...");
			if (this.getServer().getVersion().contains("Spigot")) {
				final int pluginID = 2209;
				SpigotUpdater updater = new SpigotUpdater(this, pluginID, false);
				if (updater.getResult() == SpigotUpdater.UpdateResult.UPDATE_AVAILABLE) {
					String title = "============================================";
					String titleSpace = "                                            ";
					this.getLogger().info(title);
					try {
						this.getLogger().info(titleSpace.substring(0, titleSpace.length() / 2 - "KingKits".length() + 3) + "KingKits" + titleSpace.substring(0, titleSpace.length() / 2 - "KingKits".length()));
					} catch (Exception ex) {
						this.getServer().getConsoleSender().sendMessage("KingKits");
					}
					this.getLogger().info(title);
					this.getLogger().info("A new version is available: KingKits v" + updater.getVersion());
					this.getLogger().info("Your current version: KingKits v" + updater.getCurrentVersion());
					this.getLogger().info((ConfigController.getInstance().shouldAutomaticallyUpdate() ? "KingKits auto-updater does not work for Spigot. " : "") + "Download it from: http://www.spigotmc.org/threads/kingkits.37947");
				} else {
					switch (updater.getResult()) {
						case UPDATE_AVAILABLE:
							break;
						case DISABLED:
							this.getLogger().warning("Plugin updater disabled in the updater's configuration.");
							break;
						case BAD_RESOURCE_ID:
							this.getLogger().warning("Download failed: Bad resource ID.");
							break;
						case FAIL_SPIGOT:
							this.getLogger().warning("Download failed: Could not connect to BukkitDev.");
							break;
						case FAIL_NO_VERSION:
							this.getLogger().warning("Download failed: The latest version has an incorrect title.");
							break;
						default:
							this.getLogger().info("No new update found.");
							break;
					}
				}
			} else {
				final int pluginID = 56371;
				BukkitUpdater updater = new BukkitUpdater(this, pluginID, this.getFile(), BukkitUpdater.UpdateType.NO_DOWNLOAD, false);
				if (updater.getResult() == BukkitUpdater.UpdateResult.UPDATE_AVAILABLE) {
					String header = "============================================";
					String titleSpace = "                                            ";
					this.getLogger().info(header);
					try {
						this.getLogger().info(titleSpace.substring(0, titleSpace.length() / 2 - "KingKits".length() + 3) + "KingKits" + titleSpace.substring(0, titleSpace.length() / 2 - "KingKits".length()));
					} catch (Exception ex) {
						this.getServer().getConsoleSender().sendMessage("== KingKits ==");
					}
					this.getLogger().info(header);
					this.getLogger().info("A new version is available: " + updater.getLatestName());
					this.getLogger().info("Your current version: KingKits v" + this.getDescription().getVersion());
					if (ConfigController.getInstance().shouldAutomaticallyUpdate()) {
						this.getLogger().info("Downloading " + updater.getLatestName() + "...");
						updater = new BukkitUpdater(this, pluginID, this.getFile(), BukkitUpdater.UpdateType.NO_VERSION_CHECK, false);
						BukkitUpdater.UpdateResult updateResult = updater.getResult();
						switch (updateResult) {
							case DISABLED:
								this.getLogger().warning("Plugin updater disabled in the updater's configuration.");
								break;
							case FAIL_APIKEY:
								this.getLogger().warning("Download failed: Improperly configured the server's API key in the configuration");
								break;
							case FAIL_DBO:
								this.getLogger().warning("Download failed: Could not connect to BukkitDev.");
								break;
							case FAIL_DOWNLOAD:
								this.getLogger().warning("Download failed: Could not download the file.");
								break;
							case FAIL_NOVERSION:
								this.getLogger().warning("Download failed: The latest version has an incorrect title.");
								break;
							default:
								this.getLogger().info("The latest version of KingKits has been downloaded.");
								break;
						}
					} else {
						this.getLogger().info("Download it from: " + updater.getLatestFileLink());
					}
				}
			}
		}

		try {
			if (DataStorage.getInstance() == null) {
				if (ConfigController.getInstance().getSQLDetails().isEnabled()) {
					DataStorage.setInstance(DataStorage.DataStorageType.SQL);
				} else {
					DataStorage.setInstance(DataStorage.DataStorageType.FILE);
				}
			}

			this.startAutoSaveTask();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		if (!ConfigController.getInstance().getPlayersConfig().getValues(false).isEmpty()) {
			this.allowJoining = false;
			boolean kickPlayers = !(DataStorage.getInstance() instanceof FlatFileStorage);
			this.getServer().getLogger().warning("Player data was not saved from last restart/shutdown. " + (!kickPlayers ? "Storing data..." : "Kicking all players and uploading the data."));
			if (kickPlayers) {
				for (Player onlinePlayer : this.getServer().getOnlinePlayers()) {
					onlinePlayer.kickPlayer(ChatColor.RED + "Sorry! Uploading player data..." + ChatColor.RESET + System.lineSeparator() + ChatColor.RED + "Please wait patiently.");
				}
			}
			for (final Map.Entry<String, Object> playerEntry : new HashMap<>(ConfigController.getInstance().getPlayersConfig().getValues(false)).entrySet()) {
				try {
					OfflineKitPlayer deserializedPlayer = OfflineKitPlayer.deserialize(ObjectUtilities.getMap(playerEntry.getValue()));
					deserializedPlayer.setModified(true);
					DataStorage.getInstance().savePlayer(deserializedPlayer, new Runnable() {
						@Override
						public void run() {
							ConfigController.getInstance().getPlayersConfig().set(playerEntry.getKey(), null);
							ConfigController.getInstance().savePlayersConfig();

							if (ConfigController.getInstance().getPlayersConfig().getValues(false).isEmpty()) {
								allowJoining = true;
								ConfigController.getInstance().deletePlayersConfig();
							}
						}
					});
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}

		this.getCommand("kingkits").setExecutor(new CommandListener(this));
		this.getCommand("pvpkit").setExecutor(new CommandPvPKit(this));
		this.getCommand("createkit").setExecutor(new CommandCreateKit(this));
		this.getCommand("deletekit").setExecutor(new CommandDeleteKit(this));
		this.getCommand("renamekit").setExecutor(new CommandRenameKit(this));
		this.getCommand("previewkit").setExecutor(new CommandPreviewKit(this));
		this.getCommand("createukit").setExecutor(new CommandCreateUserKit(this));
		this.getCommand("deleteukit").setExecutor(new CommandDeleteUserKit(this));
		this.getCommand("renameukit").setExecutor(new CommandRenameUserKit(this));
		this.getCommand("refill").setExecutor(new CommandRefill(this));

		Permissions.initialisePermissions();

		try {
			for (Player player : this.getServer().getOnlinePlayers()) {
				this.eventListener.handleJoinEvent(player);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void onDisable() {
		ConfigurationSerialization.unregisterClass(Kit.class);
		ConfigurationSerialization.unregisterClass(OfflineKitPlayer.class);
		ConfigurationSerialization.unregisterClass(Attribute.class);
		ConfigurationSerialization.unregisterClass(MySQLDetails.class);

		this.getServer().getScheduler().cancelTasks(this);

		GuiController.getInstance().shutdownController();

		for (KitPlayer kitPlayer : PlayerController.getInstance().getAllPlayers()) {
			Player player = kitPlayer.getBukkitPlayer();
			if (player == null) player = this.getServer().getPlayer(kitPlayer.getUniqueId());
			try {
				ConfigController.getInstance().getPlayersConfig().set(kitPlayer.getUniqueId().toString(), kitPlayer.serialize());
				if (player != null) {
					boolean inPvPWorld = Utilities.isPvPWorld(player.getWorld());
					if (inPvPWorld || kitPlayer.hasKit()) {
						if (ConfigController.getInstance().shouldRemovePotionEffectsOnReload()) {
							for (PotionEffect potionEffect : player.getActivePotionEffects())
								player.removePotionEffect(potionEffect.getType());
						}
						if (ConfigController.getInstance().shouldRemoveItemsOnReload()) {
							if (kitPlayer.hasKit() && kitPlayer.getKit().getMaxHealth() != PlayerUtilities.getDefaultMaxHealth())
								player.resetMaxHealth();
							player.getInventory().clear();
							player.getInventory().setArmorContents(null);
							player.updateInventory();
						}
						if (inPvPWorld && CompassController.getInstance().hasTarget(player.getUniqueId())) {
							CompassController.getInstance().removeTarget(player.getUniqueId());
							player.setCompassTarget(player.getWorld().getSpawnLocation());
						}
						kitPlayer.setKit(null);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		if (!ConfigController.getInstance().getPlayersConfig().getValues(false).isEmpty())
			ConfigController.getInstance().savePlayersConfig();

		Permissions.deinitialisePermissions();

		// Reset all players compasses to spawn.
		CompassController.getInstance().shutdownController();
		KitController.getInstance().shutdownController();
		PlayerController.getInstance().shutdownController();
		ConfigController.getInstance().shutdownController();
		SQLController.getInstance().shutdownController();
	}

	public boolean allowJoining() {
		return this.allowJoining;
	}

	public void saveResource(String resourcePath, boolean replace, File outFile) {
		if (resourcePath != null && !resourcePath.equals("")) {
			resourcePath = resourcePath.replace('\\', '/');
			InputStream in = this.getResource(resourcePath);
			if (in == null) {
				throw new IllegalArgumentException("The embedded resource \'" + resourcePath + "\' cannot be found in " + this.getFile());
			} else {
				int lastIndex = resourcePath.lastIndexOf(47);
				File outDir = new File(outFile.getAbsoluteFile().getParentFile(), resourcePath.substring(0, lastIndex >= 0 ? lastIndex : 0));
				if (!outDir.exists()) outDir.mkdirs();

				try {
					if (outFile.exists() && !replace) {
						this.getLogger().log(Level.WARNING, "Could not save " + outFile.getName() + " to " + outFile + " because " + outFile.getName() + " already exists.");
					} else {
						FileOutputStream ex = new FileOutputStream(outFile);
						byte[] buf = new byte[1024];

						int len;
						while ((len = in.read(buf)) > 0) ex.write(buf, 0, len);

						ex.close();
						in.close();
					}
				} catch (IOException ex) {
					this.getLogger().log(Level.SEVERE, "Could not save " + outFile.getName() + " to " + outFile, ex);
				}
			}
		} else {
			throw new IllegalArgumentException("Resource path cannot be null or empty");
		}
	}

	public void startAutoSaveTask() {
		if (ConfigController.getInstance().getAutoSavePlayerDataTime() != -1D) {
			this.autoSaveTaskID = this.getServer().getScheduler().runTaskTimer(this, new Runnable() {
				@Override
				public void run() {
					PlayerController.getInstance().saveAllPlayers();
				}
			}, (long) (ConfigController.getInstance().getAutoSavePlayerDataTime() * 60D * 20D), (long) (ConfigController.getInstance().getAutoSavePlayerDataTime() * 60D * 20D)).getTaskId();
		}
	}

	public void stopAutoSaveTask() {
		BukkitUtilities.cancelTask(this.autoSaveTaskID);
		this.autoSaveTaskID = -1;
	}

	public static KingKits getInstance() {
		return (KingKits) JavaPlugin.getProvidingPlugin(KingKits.class);
	}

}
