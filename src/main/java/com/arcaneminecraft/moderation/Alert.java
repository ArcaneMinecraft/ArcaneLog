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
	private final TextComponent TEMPLATE_DIAMOND_ALERT; // NOTE: Don't addExtra this.
	private final TextComponent TEMPLATE_COMMAND_ALERT; // NOTE: Don't addExtra this.
	private static final String TAG = "Alert";
	private static final int DIAMOND_LEVEL = 16; // AntiXRay
	private static final long DIAMOND_DELAY = 160L; // in ticks: 160 ticks = 8 seconds, enough for mining 8 diamonds with regular pickaxe
	private static final String DIAMOND_PERMISSION = "arcane.alert.receive.antixray";
	private static final String RECEIVE_ALL_CMD_PERMISSION = "arcane.alert.receive.command.all";
	private static final String RECEIVE_SUSPICIOUS_CMD_PERMISSION = "arcane.alert.receive.command.suspicious";
	private static final String EXEMPT_PERMISSION = "arcane.alert.ignore.command.common";
	private static final String OP_PERMISSION = "arcane.alert.op";
	private static final String IGNORE_CONFIG = "alert.commands.ignore";
	private static final String SUSPICIOUS_CONFIG = "alert.commands.suspicious";
	private final HashMap<Player,ReceiveLevel> modReceive = new HashMap<>(); // in case someone wants to stream or play normally
	private final HashMap<Player, DiamondCounter> diamondMineMap = new HashMap<>(); // for Diamond Timing
	private HashSet<String> cmdIgnore; // Commands to ignore for everyone, e.g. everyone receives result of this command
	private HashSet<String> cmdSuspicious; // Suspicious commands to alert at all times
	
	private static enum ReceiveLevel {
		ALL,SUSPICIOUS,NONE
	}
	
	private static final String[][] ALERT_HELP = {
		{"alert", "show this screen"},
		{"alert <on|off>", "turn alerts on or off", "if off, you won't receive diamond alerts as well."},
		{"alert suspicious", "only receive suspicious alerts", "Basically, reveive alerts at moderator level.", RECEIVE_ALL_CMD_PERMISSION},
		{"alert everything", "receive alerts for everyone", "This includes commands run by players with ignore permission.\nThis still hides ignored commands.", RECEIVE_ALL_CMD_PERMISSION},
		{"alert ignore", "list/register commands to ignore", "Usage: /alert ignore [command|-command]", OP_PERMISSION},
		{"alert toall", "list/register suspicious commands", "Usage: /alert toall [command|-command]", OP_PERMISSION},
		{"For ignore/toall, append command name to register."},
		{"Prepend \"-\" to command name to remove from list."},
	};
	
	// TODO: Develop lists

	Alert(ArcaneModeration plugin) {
		this.plugin = plugin;
		this.scheduler = plugin.getServer().getScheduler();
		cmdIgnore = new HashSet<>();
		cmdSuspicious = new HashSet<>();
		
		// Load configuration
		cmdIgnore = new HashSet<String>(plugin.getConfig().getStringList(IGNORE_CONFIG));
		cmdSuspicious = new HashSet<String>(plugin.getConfig().getStringList(SUSPICIOUS_CONFIG));
		
		// Pre-generate messages
		TextComponent alertTag = new TextComponent("<!> ");
		alertTag.setBold(true);
		alertTag.setItalic(false);
		TEMPLATE_DIAMOND_ALERT = new TextComponent();
		TEMPLATE_DIAMOND_ALERT.addExtra(alertTag);
		TEMPLATE_DIAMOND_ALERT.setColor(ColorPalette.HEADING);
		TEMPLATE_COMMAND_ALERT = new TextComponent();
		TEMPLATE_COMMAND_ALERT.addExtra(alertTag);
		TEMPLATE_COMMAND_ALERT.setColor(ChatColor.DARK_GRAY);
		TEMPLATE_COMMAND_ALERT.setItalic(true);
	}

	/**
	 * 
	 * @param p Player to get location and name out of.
	 * @param l Location to create teleport template from.
	 * @return Literally "[!] Player"
	 */
	private final void addLocationClickEvent(TextComponent tc, Location l) {
		tc.setClickEvent(new ClickEvent(Action.SUGGEST_COMMAND, "/tp "+ l.getBlockX() + ' ' + l.getBlockY() + ' ' + l.getBlockZ()));
	}
	
	private final TextComponent diamondAlertMsg(Player p, int count, Block lastBlock) {
		TextComponent ret = (TextComponent)TEMPLATE_DIAMOND_ALERT.duplicate();
		TextComponent a = new TextComponent(p.getName());
		a.setColor(ColorPalette.FOCUS);
		ret.addExtra(a);
		ret.addExtra(" just mined ");
		a = new TextComponent(count + " diamond ore" + (count == 1 ? "" : "s"));
		a.setColor(ColorPalette.FOCUS);
		ret.addExtra(a);
		ret.addExtra(".");
		addLocationClickEvent(ret,lastBlock.getLocation());
		return ret;
	}
	
	private final TextComponent commandAlertMsg(Player p, String command) {
		TextComponent ret = (TextComponent)TEMPLATE_COMMAND_ALERT.duplicate();
		TextComponent a = new TextComponent(p.getName());
		a.setColor(ChatColor.GRAY);
		ret.addExtra(a);
		ret.addExtra(" ran ");
		a = new TextComponent(command);
		a.setColor(ChatColor.GRAY);
		ret.addExtra(a);
		ret.addExtra(".");
		addLocationClickEvent(ret,p.getLocation());
		return ret;
	}
	
	public final String getAlertLevelString(ReceiveLevel rl) {
		if (rl == null)
			return ColorPalette.POSITIVE + "all";
		switch (rl) {
		case ALL:
			return ColorPalette.POSITIVE + "every single";
		case SUSPICIOUS:
			return ColorPalette.FOCUS + "suspicious";
		case NONE:
			return ColorPalette.NEGATIVE + "no";
		}
		return "";
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			
			String[] footer = {
				"You are " +
						(sender instanceof Player ? "receiving "
								+ getAlertLevelString(modReceive.get((Player)sender)) + ColorPalette.CONTENT + " alerts."
								: "a console.")
			};
			ArcaneCommons.sendCommandMenu(sender, "Alert Menu", ALERT_HELP, footer);
			return true;
		}
		if(args[0].equalsIgnoreCase("ignore") || args[0].equalsIgnoreCase("toall")) {
			boolean isIgnore = args[0].equalsIgnoreCase("ignore");
			String config = isIgnore ? IGNORE_CONFIG : SUSPICIOUS_CONFIG;
			HashSet<String> set = (isIgnore?cmdIgnore:cmdSuspicious);
			
			if (args.length == 1) {
				StringBuilder send = new StringBuilder(isIgnore ? "Ignored commands:" : "Suspicious commands:").append(ColorPalette.FOCUS);
				for (String cmd : set) {
					send.append(" /").append(cmd);
				}
				sender.sendMessage(ArcaneCommons.tag(TAG, send.toString()));
				return true;
    		}
			
			String listName = isIgnore ? "ignore list" : "toall list";
			
			for (int i = 1; i < args.length; i++) {
				String c = args[i].toLowerCase();
				boolean add = true;
				if (c.startsWith("-")) {
					c = c.substring(1);
					add = false;
				}
				if (c.startsWith("/")) c = c.substring(1);
				
				if (add) {
					if (set.add(c)) {
						plugin.getConfig().getStringList(config).add(c);
						plugin.getServer().broadcast(ArcaneCommons.tag(TAG, "/"+c+" is added to the "+listName+"."), OP_PERMISSION);
					}
					else {
						sender.sendMessage(ArcaneCommons.tag(TAG, "/"+c+" already exists in the "+listName+"."));
					}
				}
				else {
					if (set.remove(c)) {
						plugin.getConfig().getStringList(config).add(c);
						plugin.getServer().broadcast(ArcaneCommons.tag(TAG, "/"+c+" is removed from the "+listName+"."), OP_PERMISSION);
					}
					else {
						sender.sendMessage(ArcaneCommons.tag(TAG, "/"+c+" does not exist in the "+listName+"."));
					}
				}
				plugin.saveConfig();
			}
			
			return true;
		}
		if (!(sender instanceof Player)) {
			sender.sendMessage(ArcaneCommons.noConsoleMsg());
			return true;
		}
		
		ReceiveLevel rl;
		
		switch (args[0]) {
		case "off":
			rl = ReceiveLevel.NONE;
			break;
		case "on":
			rl = null;
			break;
		case "suspicious":
			if (!sender.hasPermission(RECEIVE_ALL_CMD_PERMISSION)) {
				sender.sendMessage(ArcaneCommons.noPermissionMsg("alert", "suspicious"));
				return true;
			}
			rl = ReceiveLevel.SUSPICIOUS;
			break;
		case "everything":
			if (!sender.hasPermission(RECEIVE_ALL_CMD_PERMISSION)) {
				sender.sendMessage(ArcaneCommons.noPermissionMsg("alert", "all"));
				return true;
			}
			rl = ReceiveLevel.ALL;
			break;
		default:
			return false;
		}
		
		Player p = (Player)sender;
		if (rl == null) modReceive.remove(p); 
		else modReceive.put(p, rl);
		
		sender.sendMessage(ArcaneCommons.tag(TAG, "You will receive " + getAlertLevelString(rl) + ColorPalette.CONTENT +" alerts."));
		return true;
	}
	
	// Alerts
	@EventHandler (priority=EventPriority.LOW)
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent e) {
		String m = e.getMessage();
		String cmd = m.split(" ", 2)[0].substring(1);
		
		if (cmdIgnore.contains(cmd)) return;
		
		Player p = e.getPlayer();
		TextComponent msg = commandAlertMsg(p,m);
		
		
		if (cmdSuspicious.contains(cmd)) {
			for (Player receiver : plugin.getServer().getOnlinePlayers()) {
				if (receiver.hasPermission(RECEIVE_SUSPICIOUS_CMD_PERMISSION) && modReceive.get(receiver) != ReceiveLevel.NONE) {
					receiver.spigot().sendMessage(msg);
				}
			}
			return;
		} else {
			for (Player receiver : plugin.getServer().getOnlinePlayers()) {
				if (receiver.hasPermission(RECEIVE_ALL_CMD_PERMISSION)) {
					ReceiveLevel rl = modReceive.get(receiver);
					if (rl != ReceiveLevel.NONE && ((!p.hasPermission(EXEMPT_PERMISSION) || rl == ReceiveLevel.ALL))) {
						receiver.spigot().sendMessage(msg);
					}
				}
			}
			return;
		}
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
				if (p.hasPermission(DIAMOND_PERMISSION) && !(modReceive.get(p) == ReceiveLevel.NONE)) {
					p.spigot().sendMessage(msg);
				}
			}
			plugin.getServer().getConsoleSender().sendMessage(msg.toLegacyText());
			diamondMineMap.remove(p);
		}
	}
	
}
