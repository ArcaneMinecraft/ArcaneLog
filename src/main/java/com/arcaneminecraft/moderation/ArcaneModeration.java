package com.arcaneminecraft.moderation;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.arcaneminecraft.ArcaneCommons;

public final class ArcaneModeration extends JavaPlugin {
	@Override
	public void onEnable() {
		Alert al = new Alert(this);
		getCommand("alert").setExecutor(al);
		getServer().getPluginManager().registerEvents(al, this);
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		// Changes gamemode. This is pretty awesome.
		// g0, g1, g2, g3
		if (cmd.getName().equalsIgnoreCase("g0")) {
			if (sender.hasPermission("arcane.admin") || sender.hasPermission("minecraft.command.gamemode")) {
				return ((Player)sender).performCommand("gamemode " + label.charAt(1));
			} else {
				sender.sendMessage(ArcaneCommons.noPermissionMsg(label));
				return true;
			}
		}
		return false;
	}


}
