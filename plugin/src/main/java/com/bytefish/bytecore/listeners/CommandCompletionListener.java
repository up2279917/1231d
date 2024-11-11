package com.bytefish.bytecore.listeners;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandCompletionListener implements Listener {

	private final JavaPlugin plugin;
	private final Set<String> commands;

	public CommandCompletionListener(JavaPlugin plugin) {
		this.plugin = plugin;
		this.commands = new HashSet<>(
			Arrays.asList(
				"shophelp",
				"locationhelp",
				"rules",
				"msg",
				"w",
				"tell",
				"whisper",
				"warn"
			)
		);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onTabComplete(TabCompleteEvent event) {
		String buffer = event.getBuffer().toLowerCase();

		// Handle empty or single slash
		if (buffer.isEmpty() || buffer.equals("/")) {
			event.setCompletions(
				commands.stream().sorted().collect(Collectors.toList())
			);
			return;
		}

		if (isMessageCommand(buffer)) {
			String[] parts = buffer.split(" ", 2);
			if (parts.length > 1) {
				event.setCompletions(
					plugin
						.getServer()
						.getOnlinePlayers()
						.stream()
						.map(player -> player.getName())
						.filter(name ->
							!event.getSender().getName().equals(name)
						)
						.filter(
							name ->
								parts.length < 2 ||
								name
									.toLowerCase()
									.startsWith(parts[1].toLowerCase())
						)
						.sorted()
						.collect(Collectors.toList())
				);
			}
		}
	}

	private boolean isMessageCommand(String buffer) {
		return (
			buffer.startsWith("/msg ") ||
			buffer.startsWith("/w ") ||
			buffer.startsWith("/tell ") ||
			buffer.startsWith("/whisper ")
		);
	}
}
