package com.bytefish.bytecore.api.dto;

import java.util.List;
import java.util.Map;

public class ServerStatsDTO {

	private final int playerCount;
	private final int maxPlayers;
	private final double tps;
	private final List<PlayerDTO> onlinePlayers;

	public ServerStatsDTO(
		int playerCount,
		int maxPlayers,
		double tps,
		List<PlayerDTO> onlinePlayers
	) {
		this.playerCount = playerCount;
		this.maxPlayers = maxPlayers;
		this.tps = tps;
		this.onlinePlayers = onlinePlayers;
	}

	public static class PlayerDTO {

		private final String name;
		private final boolean isOperator;
		private final Map<String, Object> extraData;

		public PlayerDTO(
			String name,
			boolean isOperator,
			Map<String, Object> extraData
		) {
			this.name = name;
			this.isOperator = isOperator;
			this.extraData = extraData;
		}

		public String getName() {
			return name;
		}

		public boolean isOperator() {
			return isOperator;
		}

		public Map<String, Object> getExtraData() {
			return extraData;
		}
	}

	public int getPlayerCount() {
		return playerCount;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	public double getTps() {
		return tps;
	}

	public List<PlayerDTO> getOnlinePlayers() {
		return onlinePlayers;
	}
}
