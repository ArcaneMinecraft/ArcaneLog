package com.arcaneminecraft.moderation;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.arcaneminecraft.ArcaneCommons;

public final class ArcaneModeration extends JavaPlugin {
	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		Alert al = new Alert(this);
		getCommand("alert").setExecutor(al);
		getServer().getPluginManager().registerEvents(al, this);
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		// Changes gamemode. This is pretty awesome.
		// g0, g1, g2, g3
		if (cmd.getName().equalsIgnoreCase("g0")) {
			if (sender.hasPermission("arcane.op") || sender.hasPermission("minecraft.command.gamemode")) {
				return ((Player)sender).performCommand("gamemode " + label.charAt(1));
			} else {
				sender.sendMessage(ArcaneCommons.noPermissionMsg(label));
				return true;
			}
		}
		
		if (cmd.getName().equalsIgnoreCase("opme")) {
			if (sender.hasPermission("arcane.op")) {
				if (sender.isOp()) {
					sender.sendMessage(ArcaneCommons.tag("OP","You are already opped. Use /deop to remove op."));
				} else {
					sender.setOp(true);
					sender.sendMessage(ArcaneCommons.tag("OP","You have been opped. Use it with care."));
				}
			} else {
				sender.sendMessage(ArcaneCommons.noPermissionMsg(label));
				return true;
			}
		}
		
		return false;
	}


}
