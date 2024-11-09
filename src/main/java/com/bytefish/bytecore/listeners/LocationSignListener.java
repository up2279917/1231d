package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.managers.LocationManager;
import com.bytefish.bytecore.models.Location;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public class LocationSignListener implements Listener {

	private final LocationManager locationManager;
	private final PlainTextComponentSerializer textSerializer =
		PlainTextComponentSerializer.plainText();

	public LocationSignListener(LocationManager locationManager) {
		this.locationManager = locationManager;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onSignChange(SignChangeEvent event) {
		String firstLine = textSerializer
			.serialize(event.line(0))
			.toLowerCase();
		if (!firstLine.equalsIgnoreCase("location")) {
			return;
		}

		Player player = event.getPlayer();
		String locationName = textSerializer.serialize(event.line(1));
		String description = textSerializer.serialize(event.line(2));

		if (locationName == null || locationName.trim().isEmpty()) {
			event.setCancelled(true);
			player.sendMessage(
				Component.text(
					"Please specify a location name on line 2!"
				).color(NamedTextColor.RED)
			);
			return;
		}

		System.out.println(
			"Attempting to get location by name: " + locationName
		);

		Optional<Location> existingLocation = locationManager.getLocationByName(
			locationName
		);
		if (existingLocation.isPresent()) {
			player.sendMessage(
				Component.text("You cannot edit this location sign!").color(
					NamedTextColor.RED
				)
			);
			event.setCancelled(true);
			return;
		}

		int currentLocations = locationManager.getPlayerLocationCount(
			player.getUniqueId().toString()
		);

		int maxLocations = locationManager.getMaxLocationsPerPlayer();
		if (currentLocations >= maxLocations) {
			event.setCancelled(true);
			player.sendMessage(
				Component.text(
					"You have reached the maximum limit of " +
					maxLocations +
					" locations!"
				).color(NamedTextColor.RED)
			);
			return;
		}

		org.bukkit.Location signLocation = event.getBlock().getLocation();
		locationManager
			.addLocation(
				locationName,
				player,
				description,
				signLocation.getWorld(),
				signLocation.getX(),
				signLocation.getY(),
				signLocation.getZ()
			)
			.ifPresentOrElse(
				location -> {
					event.line(
						0,
						Component.text("Location").color(NamedTextColor.GOLD)
					);
					event.line(
						1,
						Component.text(locationName).color(
							NamedTextColor.YELLOW
						)
					);
					if (description != null && !description.trim().isEmpty()) {
						event.line(
							2,
							Component.text(description).color(
								NamedTextColor.GRAY
							)
						);
					}
					event.line(
						3,
						Component.text(
							String.format(
								"%.0f, %.0f",
								location.getX(),
								location.getZ()
							)
						).color(NamedTextColor.AQUA)
					);

					player.sendMessage(
						Component.text(
							"Location marker created successfully!"
						).color(NamedTextColor.GREEN)
					);
				},
				() -> {
					event.setCancelled(true);
					player.sendMessage(
						Component.text(
							"Failed to create location marker. Please try again."
						).color(NamedTextColor.RED)
					);
				}
			);
	}
}
