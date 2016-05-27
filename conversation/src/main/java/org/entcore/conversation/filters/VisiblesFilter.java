package org.entcore.conversation.filters;

import static org.entcore.common.user.UserUtils.findVisibles;

import java.util.HashSet;
import java.util.Set;

import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.request.RequestUtils;

public class VisiblesFilter implements ResourcesProvider{

	private Neo4j neo;
	private Sql sql;

	public VisiblesFilter() {
		neo = Neo4j.getInstance();
		sql = Sql.getInstance();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void authorize(HttpServerRequest request, Binding binding,
			final UserInfos user, final Handler<Boolean> handler) {

		final String parentMessageId = request.params().get("In-Reply-To");
		final Set<String> ids = new HashSet<>();
		final String customReturn = "WHERE visibles.id IN {ids} RETURN DISTINCT visibles.id";
		final JsonObject params = new JsonObject();

		RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
			public void handle(final JsonObject message) {
				ids.addAll(message.getArray("to", new JsonArray()).toList());
				ids.addAll(message.getArray("cc", new JsonArray()).toList());

				final VoidHandler checkHandler = new VoidHandler() {
					protected void handle() {
						params.putArray("ids", new JsonArray(ids.toArray()));
						findVisibles(neo.getEventBus(), user.getUserId(), customReturn, params, true, true, true, new Handler<JsonArray>() {
							public void handle(JsonArray visibles) {
								handler.handle(visibles.size() == ids.size());
							}
						});
					}
				};

				if(parentMessageId == null || parentMessageId.trim().isEmpty()){
					checkHandler.handle(null);
					return;
				}

				sql.prepared(
					"SELECT m.*  " +
					"FROM conversation.messages m " +
					"WHERE m.id = ?",
					new JsonArray().add(parentMessageId),
					SqlResult.validUniqueResultHandler(new Handler<Either<String, JsonObject>>() {
						public void handle(Either<String, JsonObject> parentMsgEvent) {
							if(parentMsgEvent.isLeft()){
								handler.handle(false);
								return;
							}

							JsonObject parentMsg = parentMsgEvent.right().getValue();
							ids.remove(parentMsg.getString("from"));
							ids.removeAll(parentMsg.getArray("to", new JsonArray()).toList());
							ids.removeAll(parentMsg.getArray("cc", new JsonArray()).toList());

							checkHandler.handle(null);
						}
					}, "cc", "to"));
			}
		});

	}

}
