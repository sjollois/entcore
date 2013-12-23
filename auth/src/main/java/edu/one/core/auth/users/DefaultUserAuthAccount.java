package edu.one.core.auth.users;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import edu.one.core.common.neo4j.Neo;
import edu.one.core.infra.NotificationHelper;
import edu.one.core.infra.Server;
import edu.one.core.infra.security.BCrypt;

public class DefaultUserAuthAccount implements UserAuthAccount {

	private final Neo neo;
	private final Vertx vertx;
	private final Container container;
	private final NotificationHelper notification;

	public DefaultUserAuthAccount(Vertx vertx, Container container) {
		EventBus eb = Server.getEventBus(vertx);
		this.neo = new Neo(eb, container.logger());
		this.vertx = vertx;
		this.container = container;
		notification = new NotificationHelper(eb, container);
	}

	@Override
	public void activateAccount(final String login, String activationCode, final String password,
			final Handler<Boolean> handler) {
		String query =
				"MATCH (n:User) " +
				"WHERE n.login = {login} AND n.activationCode = {activationCode} AND n.password IS NULL " +
				"SET n.password = {password}, n.activationCode = null " +
				"RETURN n.password as password, n.id as id";
		Map<String, Object> params = new HashMap<>();
		params.put("login", login);
		params.put("activationCode", activationCode);
		params.put("password", BCrypt.hashpw(password, BCrypt.gensalt()));
		neo.send(query, params, new Handler<Message<JsonObject>>(){

			@Override
			public void handle(Message<JsonObject> res) {
				if ("ok".equals(res.body().getString("status"))
						&& res.body().getObject("result").getObject("0") != null) {
					JsonObject jo = new JsonObject().putString(
							"userId", 
							res.body().getObject("result").getObject("0").getString("id"));
					Server.getEventBus(vertx).publish(
							container.config().getString("address.activation", "wse.activation.hack"),
							jo);
					handler.handle(true);
				} else {
					String q =
							"MATCH (n:User) " +
							"WHERE n.login = {login} AND n.activationCode IS NULL " +
							"AND NOT(n.password IS NULL) " +
							"RETURN n.password as password";
					Map<String, Object> p = new HashMap<>();
					p.put("login", login);
					neo.send(q, p, new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> event) {
							handler.handle(
									"ok".equals(event.body().getString("status")) &&
									event.body().getObject("result").getObject("0") != null &&
									BCrypt.checkpw(password, event.body().getObject("result").getObject("0")
											.getString("password", ""))
							);
						}
					});
				}
			}
		});
	}

	@Override
	public void forgotPassword(final HttpServerRequest request, String login,
			final Handler<Boolean> handler) {
		String query =
				"MATCH (n:User) " +
				"WHERE n.login = {login} AND NOT(n.email IS NULL) AND n.activationCode IS NULL " +
				"SET n.resetCode = {resetCode} " +
				"RETURN n.email as email";
		final String query2 =
				"MATCH (n:Student)-[:APPARTIENT]->(m:Class)<-[:APPARTIENT]-(p:Teacher) " +
				"WHERE n.login = {login} AND NOT(p.email IS NULL) AND n.activationCode IS NULL " +
				"SET n.resetCode = {resetCode} " +
				"RETURN p.email as email";
		final Map<String, Object> params = new HashMap<>();
		params.put("login", login);
		final String resetCode = UUID.randomUUID().toString();
		params.put("resetCode", resetCode);
		neo.send(query, params, new Handler<Message<JsonObject>>(){

			@Override
			public void handle(Message<JsonObject> res) {
				if ("ok".equals(res.body().getString("status"))) {
					JsonObject json = res.body().getObject("result");
					if (json.getObject("0") != null &&
							json.getObject("0").getString("email") != null &&
							!json.getObject("0").getString("email").trim().isEmpty()) {
						sendResetPasswordLink(request, json.getObject("0")
								.getString("email"), resetCode, handler);
					} else {
						neo.send(query2, params, new Handler<Message<JsonObject>>(){

							@Override
							public void handle(Message<JsonObject> event) {
								JsonObject j = event.body().getObject("result");
								if ("ok".equals(event.body().getString("status")) &&
										j.getObject("0") != null &&
										j.getObject("0").getString("email") != null &&
										!j.getObject("0").getString("email").trim().isEmpty()) {
									sendResetPasswordLink(request, j.getObject("0")
											.getString("email"), resetCode, handler);
								} else {
									handler.handle(false);
								}
							}
						});
					}
				} else {
					handler.handle(false);
				}
			}
		});
	}

	private void sendResetPasswordLink(HttpServerRequest request, String email, String resetCode,
			final Handler<Boolean> handler) {
		if (email == null || resetCode == null || email.trim().isEmpty() || resetCode.trim().isEmpty()) {
			handler.handle(false);
			return;
		}
		JsonObject json = new JsonObject()
		.putString("resetUri", container.config()
				.getString("host", "http://localhost:8009") + "/auth/reset/" + resetCode);
		container.logger().debug(json.encode());
		try {
			notification.sendEmail(request, email, container.config()
					.getString("email", "noreply@one1d.fr"), null, null,
					"mail.reset.pw.subject", "email/forgotPassword.txt", json, true,
					new Handler<Message<JsonObject>>() {

				@Override
				public void handle(Message<JsonObject> message) {
					handler.handle("ok".equals(message.body().getString("status")));
				}
			});
		} catch (IOException e) {
			container.logger().error("Error sending reset password email", e);
			handler.handle(false);
		}
	}

	@Override
	public void resetPassword(String login, String resetCode, String password, final Handler<Boolean> handler) {
		String query =
				"MATCH (n:User) " +
				"WHERE n.login = {login} AND n.resetCode = {resetCode} " +
				"SET n.password = {password}, n.resetCode = null " +
				"RETURN n.password as pw";
		Map<String, Object> params = new HashMap<>();
		params.put("login", login);
		params.put("resetCode", resetCode);
		updatePassword(handler, query, password, params);
	}

	@Override
	public void changePassword(String login, String password, final Handler<Boolean> handler) {
		String query =
				"MATCH (n:User) " +
				"WHERE n.login = {login} AND NOT(n.password IS NULL) " +
				"SET n.password = {password} " +
				"RETURN n.password as pw";
		Map<String, Object> params = new HashMap<>();
		params.put("login", login);
		updatePassword(handler, query, password, params);
	}

	@Override
	public void sendResetCode(final HttpServerRequest request, String login, final String email,
			final Handler<Boolean> handler) {
		String query =
				"MATCH (n:User) " +
				"WHERE n.login = {login} AND n.activationCode IS NULL " +
				"SET n.resetCode = {resetCode} " +
				"RETURN count(n) as nb";
		final String code = UUID.randomUUID().toString();
		JsonObject params = new JsonObject().putString("login", login).putString("resetCode", code);
		neo.execute(query, params, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				if ("ok".equals(event.body().getString("status")) &&
						event.body().getArray("result") != null && event.body().getArray("result").size() == 1 &&
						"1".equals(((JsonObject) event.body().getArray("result").get(0)).getString("nb"))) {
					sendResetPasswordLink(request, email, code, handler);
				} else {
					handler.handle(false);
				}
			}
		});
	}

	private void updatePassword(final Handler<Boolean> handler, String query, String password, Map<String, Object> params) {
		final String pw = BCrypt.hashpw(password, BCrypt.gensalt());
		params.put("password", pw);
		neo.send(query, params, new Handler<Message<JsonObject>>(){

			@Override
			public void handle(Message<JsonObject> res) {
				JsonObject r = res.body().getObject("result");
				handler.handle("ok".equals(res.body().getString("status"))
						&& r.getObject("0") != null
						&& pw.equals(r.getObject("0").getString("pw")));
			}
		});
	}

}
