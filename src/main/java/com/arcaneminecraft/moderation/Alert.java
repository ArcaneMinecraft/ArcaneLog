package com.arcaneminecraft.moderation;

import java.util.HashMap;
import java.util.HashSet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.metadata.Metadatable;
import org.bukkit.scheduler.BukkitScheduler;

import com.arcaneminecraft.ArcaneCommons;
import com.arcaneminecraft.ColorPalette;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;

public class Alert implements CommandExecutor, Listener {
	private final ArcaneModeration plugin;
	private final BukkitScheduler scheduler;
	private final TextComponent TAG_TC; // NOTE: Don't addExtra this.
	private static final String TAG = "Alert";
	private static final int DIAMOND_LEVEL = 16; // AntiXRay
	private static final long DIAMOND_DELAY = 160L; // in ticks: 160 ticks = 8 seconds, enough for mining 8 diamonds with regular pickaxe
	private static final String DIAMOND_PERMISSION = "arcane.alert.receive.antixray";
	private static final String ALL_CMD_PERMISSION = "arcane.alert.receive.command.all";
	private static final String SUSPICIOUS_CMD_PERMISSION = "arcane.alert.receive.command.suspicious";
	private static final String EXEMPT_PERMISSION = "arcane.alert.ignore";
	private static final String OP_PERMISSION = "arcane.alert.op";
	private final HashSet<Player> ignoreAllAlerts = new HashSet<>(); // in case someone wants to stream or play normally
	private final HashMap<Player, DiamondCounter> diamondMineMap = new HashMap<>(); // for Diamond Timing
	
	private static final String[][] ALERT_HELP = {
		{"alert", "show this screen"},
		{"alert toggle", "toggle to receive or not receive any alerts."},
		{"alert hide", "show ", null, OP_PERMISSION},
		{"alert suspicious", "show ", null, OP_PERMISSION}
	};
	/*
	sender.sendMessage("Alert management module: /simonplugin alert ...");
	sender.sendMessage("§7> <on|off> - turns the alerts on/off for all staff.");
	sender.sendMessage("§7> <exempt|notifyall|flags> - returns exempted/staff-broadcasted commands.");
	sender.sendMessage("§7> <exempt|notifyall> [command|-command]... - adds or removes flags.");
	*/

	Alert(ArcaneModeration plugin) {
		this.plugin = plugin;
		this.scheduler = plugin.getServer().getScheduler();
		
		TAG_TC = new TextComponent("[");
		TextComponent inner = new TextComponent("!");
		inner.setColor(ChatColor.RED);
		inner.setBold(false);
		TAG_TC.addExtra(inner);
		TAG_TC.addExtra("]");
		TAG_TC.setColor(ChatColor.RED);
		TAG_TC.setBold(true);
	}

	/**
	 * 
	 * @param p Player to get location and name out of.
	 * @param l Location to create teleport template from.
	 * @return Literally "[!] Player"
	 */
	private final TextComponent messageConstructor(Player p, Location l) {
		TextComponent ret = new TextComponent();
		ret.setColor(ColorPalette.CONTENT);
		ret.addExtra(TAG_TC);
		ret.addExtra(" " + p.getName());
		ret.setClickEvent(new ClickEvent(Action.SUGGEST_COMMAND, "/tp "+ l.getBlockX() + ' ' + l.getBlockY() + ' ' + l.getBlockZ()));
		return ret;
	}
	
	private final TextComponent diamondAlertMsg(Player p, int count, Block lastBlock) {
		TextComponent ret = messageConstructor(p, lastBlock.getLocation());
		ret.addExtra(" ");
		TextComponent a = new TextComponent("just mined");
		a.setColor(ChatColor.DARK_GRAY);
		ret.addExtra(a);
		ret.addExtra(" " + count + " diamond ore" + (count == 1 ? "" : "s") + ".");
		return ret;
	}
	
	private final TextComponent commandAlertMsg(Player p, String command) {
		TextComponent ret = messageConstructor(p, p.getLocation());
		TextComponent a = new TextComponent(":");
		a.setColor(ChatColor.DARK_GRAY);
		ret.addExtra(a);
		ret.addExtra(" " + command);
		return ret;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			String[] footer = {
				"You are " +
						(sender instanceof Player ? "receiving "
								+ (ignoreAllAlerts.contains((Player)sender) ? ColorPalette.NEGATIVE + "no" : ColorPalette.POSITIVE + "all")
								+ ColorPalette.CONTENT + " alerts."
								: "a console.")
			};
			ArcaneCommons.sendCommandMenu(sender, "Alert Menu", ALERT_HELP, footer);
			return true;
		}
		if(args.length > 1) {
			if(args[0].equalsIgnoreCase("toggle")) {
				if (!(sender instanceof Player)) {
					sender.sendMessage(ArcaneCommons.noConsoleMsg());
					return true;
				}
				Player p = (Player)sender;
				
				if (ignoreAllAlerts.add(p)){
					sender.sendMessage(ArcaneCommons.tag(TAG, "Alerts has been toggled " + ColorPalette.NEGATIVE + "off" + ColorPalette.CONTENT + "."));
				} else {
					ignoreAllAlerts.remove(p);
					sender.sendMessage(ArcaneCommons.tag(TAG, "Alerts has been toggled " + ColorPalette.POSITIVE + "on" + ColorPalette.CONTENT + "."));
				}
				
				return true;
			}
			/* TODO
			if(!args[0].equalsIgnoreCase("exempt"))
				sender.sendMessage("§7NotifyAll: " + Arrays.toString(ALERT_EXEMPT.toArray(new String[0])));
			if(!args[0].equalsIgnoreCase("notifyall"))
				sender.sendMessage("§7Exempted: " + Arrays.toString(ALERT_TOALL.toArray(new String[0])));
				*/
			return true;
		}
		/*
		if(!args[1].equalsIgnoreCase("exempt")&&!args[1].equalsIgnoreCase("notifyall")) {
			sender.sendMessage("§7> <exempt|notifyall> [command|-command]... - adds or removes flags.");
			return true;
		}
		HashSet<String> S = null;
		S = (args[1].equalsIgnoreCase("exempt")?ALERT_EXEMPT:ALERT_TOALL);
		for(int i=2; i < args.length;i++) {
			String[] t = {args[1].toLowerCase(),args[i]};
			if(args[i].startsWith("-")){
				t[1] = t[1].substring(1);
				if(S.remove(t)) {
					this.getConfig().getStringList("alert.commands."+t[0]).remove(t[1]);
					Bukkit.broadcast(ALERT_TAG+"§7/"+t[1]+" removed from "+t[0]+".", "simonplugin.op");
				}
				else {
					sender.sendMessage(ALERT_TAG+"§7/"+t[1]+" does not exist in "+t[0]+".");
				}
			}
			else {
				if(S.add(args[i])) {
					this.getConfig().getStringList("alert.commands."+t[0]).add(t[1]);
					Bukkit.broadcast(ALERT_TAG+"§7/"+t[1]+" added to "+t[0]+".", "simonplugin.op");
				}
				else {
					sender.sendMessage(ALERT_TAG+"§7/"+t[1]+" already exists in "+t[0]+".");
				}
			}
		}
		*/
		return true;
	}
	
	// Alerts
	@EventHandler (priority=EventPriority.LOW)
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent e) {
		Object[] pm = {e.getPlayer().getName(),e.getMessage()};
		String cmd = e.getMessage().split(" ", 2)[0].substring(1);
		/*
		if (ALERT_TOALL.contains(cmd))
			Bukkit.broadcast(ALERT_FORMAT.format(pm), "arcane.alert");
		else if (!ALERT_EXEMPT.contains(cmd)||!e.getPlayer().hasPermission("simonplugin.trusted"))
			Bukkit.broadcast(ALERT_FORMAT.format(pm), "arcane.alert");
			*/
	}
	
	// AntiXRay
	@EventHandler
	public void onBlockBreak(BlockBreakEvent e) {
		Block b = e.getBlock();
		Player p = e.getPlayer();
		if (b.getY() <= DIAMOND_LEVEL && b.getType() == Material.DIAMOND_ORE)
		{
			DiamondCounter c = diamondMineMap.get(p);
			if (c == null) {
				c = new DiamondCounter(p,b);
				diamondMineMap.put(p, c);
				scheduler.runTaskLater(plugin, c, DIAMOND_DELAY);
			} else {
				c.increment(b);
			}
		}
	}
	
	// Runnable DiamondCounter Class for counting mined diamonds
	private class DiamondCounter implements Runnable {
		private final Player p;
		private int count = 1;
		private Block lastMined;
		
		DiamondCounter(Player p, Block justMined) {
			this.lastMined = justMined;
			this.p = p;
		}
		
		void increment(Block justMined) {
			lastMined = justMined;
			count++;
		}

		@Override
		public void run() {
			TextComponent msg = diamondAlertMsg(p, count, lastMined);
			for (Player p : plugin.getServer().getOnlinePlayers()) {
				if (p.hasPermission(DIAMOND_PERMISSION)) {
					p.spigot().sendMessage(msg);
				}
			}
			plugin.getServer().getConsoleSender().sendMessage(msg.toLegacyText());
			diamondMineMap.remove(p);
		}
	}
	
}
