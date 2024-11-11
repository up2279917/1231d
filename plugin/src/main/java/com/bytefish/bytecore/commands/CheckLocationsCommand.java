package com.bytefish.bytecore.commands;

import com.bytefish.bytecore.managers.LocationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class CheckLocationsCommand implements CommandExecutor {

	private final LocationManager locationManager;

	public CheckLocationsCommand(LocationManager locationManager) {
		this.locationManager = locationManager;
	}

	@Override
	public boolean onCommand(
		@NotNull CommandSender sender,
		@NotNull Command command,
		@NotNull String label,
		String[] args
	) {
		if (!sender.hasPermission("bytecore.checklocations")) {
			sender.sendMessage(
				Component.text(
					"You don't have permission to use this command!"
				).color(NamedTextColor.RED)
			);
			return true;
		}

		sender.sendMessage(
			Component.text("Validating all locations...").color(
				NamedTextColor.YELLOW
			)
		);

		int beforeCount = locationManager.getAllLocations().size();
		locationManager.validateAllLocations();
		int afterCount = locationManager.getAllLocations().size();
		int removedCount = beforeCount - afterCount;

		if (removedCount > 0) {
			sender.sendMessage(
				Component.text()
					.append(Component.text("Removed ", NamedTextColor.GREEN))
					.append(Component.text(removedCount, NamedTextColor.YELLOW))
					.append(
						Component.text(
							" invalid locations. ",
							NamedTextColor.GREEN
						)
					)
					.append(Component.text(afterCount, NamedTextColor.YELLOW))
					.append(
						Component.text(
							" locations remain.",
							NamedTextColor.GREEN
						)
					)
					.build()
			);
		} else {
			sender.sendMessage(
				Component.text()
					.append(Component.text("All ", NamedTextColor.GREEN))
					.append(Component.text(afterCount, NamedTextColor.YELLOW))
					.append(
						Component.text(
							" locations are valid!",
							NamedTextColor.GREEN
						)
					)
					.build()
			);
		}

		return true;
	}
}
