/*
 * Copyright © "Open Digital Education", 2016
 *
 * This program is published by "Open Digital Education".
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https://opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.

 */

package org.entcore.directory.security;

import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;

import java.util.Map;


public class AdmlOfStructureOrClass implements ResourcesProvider {

    private final Neo4j neo = Neo4j.getInstance();

    @Override
    public void authorize(final HttpServerRequest request, Binding binding, final UserInfos user, final Handler<Boolean> handler) {
        Map<String, UserInfos.Function> functions = user.getFunctions();
        if (functions == null || functions.isEmpty()) {
            handler.handle(false);
            return;
        }
        final UserInfos.Function adminLocal = functions.get(DefaultFunctions.ADMIN_LOCAL);
        final UserInfos.Function classAdmin = functions.get(DefaultFunctions.CLASS_ADMIN);
        if ((adminLocal == null || adminLocal.getScope() == null) &&
                (classAdmin == null || classAdmin.getScope() == null)) {
            handler.handle(false);
            return;
        }
        final String classId = request.params().get("classId");
        final String structureId = request.params().get("structureId");
        if (adminLocal != null && adminLocal.getScope() != null &&
                (adminLocal.getScope().contains(structureId) || adminLocal.getScope().contains(classId))) {
            handler.handle(true);
            return;
        }
        if (adminLocal != null && classId != null && adminLocal.getScope() != null) {
            String query =
                    "MATCH (s:Structure)<-[:BELONGS]-(c:Class {id : {classId}}) " +
                            "WHERE s.id IN {ids} " +
                            "RETURN count(*) > 0 as exists";
            JsonObject params = new JsonObject()
                    .put("classId", classId)
                    .put("ids", new fr.wseduc.webutils.collections.JsonArray(adminLocal.getScope()));
            validateQuery(request, handler, query, params);
        } else {
            handler.handle(false);
        }
    }


    private void validateQuery(final HttpServerRequest request, final Handler<Boolean> handler, String query, JsonObject params) {
        request.pause();
        neo.execute(query, params, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> r) {
                request.resume();
                JsonArray res = r.body().getJsonArray("result");
                handler.handle(
                        "ok".equals(r.body().getString("status")) &&
                                res.size() == 1 && ((JsonObject) res.getJsonObject(0)).getBoolean("exists", false)
                );
            }
        });
    }
}
