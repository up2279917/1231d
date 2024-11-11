package com.bytefish.bytecore.models;

import java.time.Instant;
import java.util.UUID;

public class Warning {

	private final UUID id;
	private final String playerName;
	private final UUID playerUUID;
	private final String staffMember;
	private final UUID staffUUID;
	private final String reason;
	private final long timestamp;
	private final String serverName;

	public Warning(
		String playerName,
		UUID playerUUID,
		String staffMember,
		UUID staffUUID,
		String reason,
		String serverName
	) {
		this.id = UUID.randomUUID();
		this.playerName = playerName;
		this.playerUUID = playerUUID;
		this.staffMember = staffMember;
		this.staffUUID = staffUUID;
		this.reason = reason;
		this.timestamp = Instant.now().getEpochSecond();
		this.serverName = serverName;
	}

	public UUID getId() {
		return id;
	}

	public String getPlayerName() {
		return playerName;
	}

	public UUID getPlayerUUID() {
		return playerUUID;
	}

	public String getStaffMember() {
		return staffMember;
	}

	public UUID getStaffUUID() {
		return staffUUID;
	}

	public String getReason() {
		return reason;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getServerName() {
		return serverName;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Warning warning = (Warning) o;
		return id.equals(warning.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
