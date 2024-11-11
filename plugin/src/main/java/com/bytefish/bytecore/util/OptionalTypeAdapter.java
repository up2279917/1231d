package com.bytefish.bytecore.util;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.Optional;

public class OptionalTypeAdapter<T>
	implements JsonSerializer<Optional<T>>, JsonDeserializer<Optional<T>> {

	private final Class<T> type;

	public OptionalTypeAdapter(Class<T> type) {
		this.type = type;
	}

	@Override
	public JsonElement serialize(
		Optional<T> src,
		Type typeOfSrc,
		JsonSerializationContext context
	) {
		return src.map(context::serialize).orElse(JsonNull.INSTANCE);
	}

	@Override
	public Optional<T> deserialize(
		JsonElement json,
		Type typeOfT,
		JsonDeserializationContext context
	) {
		return Optional.ofNullable(
			json.isJsonNull() ? null : context.deserialize(json, type)
		);
	}
}
