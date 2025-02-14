package org.entcore.directory.services;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface SlotProfileService {

    void createSlot(String idSlotProfile, JsonObject slot, Handler<Either<String, JsonObject>> handler);

    void listSlots(String idSlotProfile, Handler<Either<String, JsonObject>> handler);

    void listSlotProfilesByStructure(String structureId, Handler<Either<String, JsonArray>> handler);

    void updateSlot(String idSlotProfile, JsonObject slot, Handler<Either<String, JsonObject>> handler);

    void deleteSlotFromSlotProfile(String idSlotProfile, String idSlot, Handler<Either<String, JsonObject>> handler);

    void updateSlotProfile(String idSlotProfile, String name, Handler<Either<String, JsonObject>> handler);
}
