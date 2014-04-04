package org.entcore.communication.controllers;

import java.util.*;

import org.entcore.common.neo4j.StatementsBuilder;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonElement;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import fr.wseduc.webutils.collections.Joiner;

import org.entcore.communication.profils.GroupProfil;
import org.entcore.communication.profils.ProfilFactory;
import fr.wseduc.webutils.Controller;
import org.entcore.common.neo4j.Neo;
import fr.wseduc.webutils.Server;
import org.entcore.common.user.UserUtils;
import org.entcore.common.user.UserInfos;
import fr.wseduc.security.SecuredAction;

public class CommunicationController extends Controller {

	private final Neo neo;
	private final ProfilFactory pf;
	private static final List<String> profils = Collections.unmodifiableList(
			Arrays.asList("SuperAdmin", "Student", "Relative", "Principal", "Teacher"));

	public CommunicationController(Vertx vertx, Container container,
			RouteMatcher rm, Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super(vertx, container, rm, securedActions);
		neo = new Neo(Server.getEventBus(vertx), log);
		pf = new ProfilFactory();
	}

	@SecuredAction("communication.view")
	public void view(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

			@Override
			public void handle(UserInfos user) {
				if (user != null) {
					JsonArray functions = null;
					if (user.getFunctionCodes() != null) {
						functions = new JsonArray(user.getFunctionCodes().toArray());
					}
					listVisiblesStructures(user.getUserId(), user.getType(), functions, new Handler<JsonArray>() {

						@Override
						public void handle(JsonArray event) {
							renderView(request, new JsonObject().putArray("schools", event));
						}
					});
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@SecuredAction("communication.view")
	public void staticView(final HttpServerRequest request) {
		renderView(request);
	}

	@SecuredAction("communication.conf.profils.matrix")
	public void setGroupsProfilsMatrix(final HttpServerRequest request) {
		request.expectMultiPart(true);
		request.endHandler(new VoidHandler() {
			@Override
			protected void handle() {
				final List<String> groupsProfils = request.formAttributes().getAll("groupProfil");
				if (profils != null) {
					Set<String> gpId = new HashSet<>(Arrays.asList(Joiner.on("_").join(groupsProfils).split("_")));
					neo.send(
						"MATCH (n:ProfileGroup) " +
						"WHERE n.id IN ['" + Joiner.on("','").join(gpId) + "'] " +
						"RETURN n.id as id, n.name as name, " +
						"HEAD(filter(x IN labels(n) WHERE x <> 'Visible' AND x <> 'ProfileGroup' " +
						"AND x <> 'ClassProfileGroup' AND x <> 'SchoolProfileGroup')) as type",
						 new Handler<Message<JsonObject>>() {

						@Override
						public void handle(Message<JsonObject> res) {
							if ("ok".equals(res.body().getString("status"))) {
								HashMap<String, GroupProfil> gps = new HashMap<>();
								JsonObject result = res.body().getObject("result");
								if (result != null) {
									for (String attr : result.getFieldNames()) {
										JsonObject json = result.getObject(attr);
										try {
											gps.put(json.getString("id"), pf.getGroupProfil(json));
										} catch (IllegalArgumentException e) {
											log.error(e.getMessage(), e);
										}
									}
									JsonArray queries = new JsonArray();
									for (String gp : groupsProfils) {
										String[] groupsId = gp.split("_");
										if (groupsId.length != 2) continue;
										GroupProfil gp1 = gps.get(groupsId[0]);
										GroupProfil gp2 = gps.get(groupsId[1]);
										if (gp1 != null && gp2 != null) {
											JsonElement q = gp1.queryAddCommunicationLink(gp2);
											if (q != null) {
												if (q.isArray()) {
													for (Object o: q.asArray()) {
														queries.add(o);
													}
												} else {
													queries.add(q);
												}
											}
										}
									}
									neo.sendBatch(queries, request.response());
								} else {
									renderJson(request, res.body(), 404);
								}
							} else {
								renderError(request, res.body());
							}
						}
					});
				} else {
					badRequest(request);
				}
			}
		});
	}

	@SecuredAction("communication.conf.pe.com")
	public void setParentEnfantCommunication(final HttpServerRequest request) {
		request.expectMultiPart(true);
		request.endHandler(new VoidHandler() {
			@Override
			protected void handle() {
				final List<String> groups = request.formAttributes().getAll("groupId");
				if (groups != null) {
					JsonArray queries = GroupProfil.queryParentEnfantCommunication(groups);
					neo.sendBatch(queries, request.response());
				} else {
					badRequest(request);
				}
			}
		});
	}

	@SecuredAction("communication.list.profils")
	public void listProfils(HttpServerRequest request) {
		renderJson(request, new JsonArray(profils.toArray()));
	}

	@SecuredAction("communication.list.group.profil")
	public void listVisiblesGroupsProfil(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

			@Override
			public void handle(UserInfos user) {
				if (user != null) {
					String schoolId = request.params().get("schoolId");
					if ("SuperAdmin".equals(user.getType())) {
						eb.send("directory", new JsonObject().putString("action", "groups")
								.putString("schoolId", schoolId),
								new Handler<Message<JsonObject>>() {
							@Override
							public void handle(Message<JsonObject> res) {
								if ("ok".equals(res.body().getString("status"))) {
									renderJson(request,
											resultToJsonArray(res.body().getObject("result")));
								} else {
									renderError(request, res.body());
								}
							}
						});
					} else {
						visibleUsers(user.getUserId(), schoolId, allGroupsProfilsClasse(),
								new Handler<JsonArray>() {

							@Override
							public void handle(JsonArray event) {
								renderJson(request, event);
							}
						});
					}
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@SecuredAction("communication.list.classes.student")
	public void listVisiblesClassesEnfants(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

			@Override
			public void handle(UserInfos user) {
				if (user != null) {
					String schoolId = request.params().get("schoolId");
					if ("SuperAdmin".equals(user.getType())) {
						eb.send("directory", new JsonObject().putString("action", "groups")
								.putArray("types", new JsonArray().addString("ClassStudentGroup"))
								.putString("schoolId", schoolId),
								new Handler<Message<JsonObject>>() {
							@Override
							public void handle(Message<JsonObject> res) {
								if ("ok".equals(res.body().getString("status"))) {
									renderJson(request,
											resultToJsonArray(res.body().getObject("result")));
								} else {
									renderError(request, res.body());
								}
							}
						});
					} else {
						visibleUsers(user.getUserId(), schoolId, new JsonArray().add("ClassStudentGroup"),
								new Handler<JsonArray>() {
							@Override
							public void handle(JsonArray event) {
								renderJson(request, event);
							}
						});
					}
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@SecuredAction("communication.list.structures")
	public void listVisiblesStructures(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {

			@Override
			public void handle(UserInfos user) {
				if (user != null) {
					JsonArray functions = null;
					if (user.getFunctionCodes() != null) {
						functions = new JsonArray(user.getFunctionCodes().toArray());
					}
					listVisiblesStructures(user.getUserId(), user.getType(), functions, new Handler<JsonArray>() {

						@Override
						public void handle(JsonArray event) {
							renderJson(request, event);
						}
					});
				} else {
					unauthorized(request);
				}
			}
		});
	}

	public void listVisiblesStructures(final Message<JsonObject> message) {
		String userId = message.body().getString("userId");
		String userType = message.body().getString("userType");
		JsonArray functions = message.body().getArray("userFunctions");
		if (userId != null && !userId.trim().isEmpty()) {
			listVisiblesStructures(userId, userType, functions, new Handler<JsonArray>() {

				@Override
				public void handle(JsonArray event) {
					message.reply(event);
				}
			});
		} else {
			message.reply(new JsonArray());
		}
	}

	private void listVisiblesStructures(String userId, String userType, JsonArray functions,
			final Handler<JsonArray> handler) {
		String query;
		Map<String, Object> params = new HashMap<>();
		if (functions != null && functions.contains("SUPER_ADMIN")) {
			query = "MATCH (n:Structure) ";
		} else {
			query = "MATCH (m:User)-[:COMMUNIQUE]->g-[:DEPENDS*1..2]->(n:Structure) " +
					"WHERE m.id = {userId} ";
			params.put("userId", userId);
		}
		query += "RETURN n.id as id, n.name as name, HEAD(labels(n)) as type";
		neo.send(query, params, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> m) {
				if ("ok".equals(m.body().getString("status"))) {
					handler.handle(resultToJsonArray(m.body().getObject("result")));
				} else {
					handler.handle(new JsonArray());
				}
			}
		});
	}

	private JsonArray allGroupsProfilsClasse() {
		JsonArray gpc = new JsonArray();
		gpc.addString("ClassProfileGroup");
		return gpc;
	}

	@SecuredAction("communication.visible.user")
	public void visibleUsers(final HttpServerRequest request) {
		String userId = request.params().get("userId");
		if (userId != null && !userId.trim().isEmpty()) {
			String schoolId = request.params().get("schoolId");
			List<String> expectedTypes = request.params().getAll("expectedType");
			visibleUsers(userId, schoolId, new JsonArray(expectedTypes.toArray()), new Handler<JsonArray>() {

				@Override
				public void handle(JsonArray res) {
					renderJson(request, res);
				}
			});
		} else {
			renderJson(request, new JsonArray());
		}
	}

	public void visibleUsers(final Message<JsonObject> message) {
		String userId = message.body().getString("userId");
		if (userId != null && !userId.trim().isEmpty()) {
			String action = message.body().getString("action", "");
			String schoolId = message.body().getString("schoolId");
			JsonArray expectedTypes = message.body().getArray("expectedTypes");
			Handler<JsonArray> responseHandler = new Handler<JsonArray>() {

				@Override
				public void handle(JsonArray res) {
					message.reply(res);
				}
			};
			switch (action) {
			case "visibleUsers":
				String customReturn = message.body().getString("customReturn");
				JsonObject ap = message.body().getObject("additionnalParams");
				boolean itSelf = message.body().getBoolean("itself", false);
				boolean myGroup = message.body().getBoolean("mygroup", false);
				boolean profile = message.body().getBoolean("profile", true);
				Map<String, Object> additionnalParams = null;
				if (ap != null) {
					additionnalParams = ap.toMap();
				}
				visibleUsers(userId, schoolId, expectedTypes, itSelf, myGroup, profile, customReturn,
						additionnalParams, responseHandler);
				break;
			case "usersCanSeeMe":
				usersCanSeeMe(userId, responseHandler);
				break;
			case "visibleProfilsGroups":
				String c = message.body().getString("customReturn");
				JsonObject p = message.body().getObject("additionnalParams");
				visibleProfilsGroups(userId, c, p, responseHandler);
				break;
			case "usersInProfilGroup":
				boolean itSelf2 = message.body().getBoolean("itself", false);
				String excludeUserId = message.body().getString("excludeUserId");
				usersInProfilGroup(userId, itSelf2, excludeUserId, responseHandler);
				break;
			default:
				message.reply(new JsonArray());
				break;
			}
		} else {
			message.reply(new JsonArray());
		}
	}

	private void visibleUsers(String userId, String schoolId, JsonArray expectedTypes,
			final Handler<JsonArray> handler) {
		visibleUsers(userId, schoolId, expectedTypes, false, null, null, handler);
	}

	private void visibleUsers(String userId, String schoolId, JsonArray expectedTypes, boolean itSelf,
			String customReturn, Map<String, Object> additionnalParams, final Handler<JsonArray> handler) {
		visibleUsers(userId, schoolId, expectedTypes, itSelf, false, true, customReturn, additionnalParams, handler);
	}

	private void visibleUsers(String userId, String schoolId, JsonArray expectedTypes, boolean itSelf, boolean myGroup,
			boolean profile,
			String customReturn, Map<String, Object> additionnalParams, final Handler<JsonArray> handler) {
		StringBuilder query = new StringBuilder();
		Map<String, Object> params = new HashMap<>();
		String condition = itSelf ? "" : "AND m.id <> {userId} ";
		if (schoolId != null && !schoolId.trim().isEmpty()) {
			query.append("MATCH (n:User)-[:COMMUNIQUE*1..3]->m-[:DEPENDS*1..2]->(s:Structure {id:{schoolId}})"); //TODO manage leaf
			params.put("schoolId", schoolId);
		} else {
			String l = (myGroup) ? "" : " AND length(p) >= 2";
			query.append(" MATCH p=(n:User)-[r:COMMUNIQUE|COMMUNIQUE_DIRECT]->t-[:COMMUNIQUE*0..1]->ipg" +
					"-[:COMMUNIQUE*0..1]->g<-[:DEPENDS*0..1]-m ");
			condition += "AND ((type(r) = 'COMMUNIQUE_DIRECT' AND length(p) = 1) " +
					"XOR (type(r) = 'COMMUNIQUE'"+ l +
					" AND (length(p) < 3 OR (ipg:ProfileGroup AND length(p) = 3)))) ";
		}
		query.append("WHERE n.id = {userId} AND (NOT(HAS(m.blocked)) OR m.blocked = false) ").append(condition);
		if (expectedTypes != null && expectedTypes.size() > 0) {
			query.append("AND (");
			StringBuilder types = new StringBuilder();
			for (Object o: expectedTypes) {
				if (!(o instanceof String)) continue;
				String t = (String) o;
				types.append(" OR m:").append(t);
			}
			query.append(types.substring(4)).append(") ");
		}
		String pcr = " ";
		String pr = "";
		if (profile) {
			query.append("OPTIONAL MATCH m-[:IN*0..1]->pgp-[:DEPENDS*0..1]->(pg:ProfileGroup)-[:HAS_PROFILE]->(profile:Profile) ");
			pcr = ", profile ";
			pr = "profile.name as type, ";
		}
		if (customReturn != null && !customReturn.trim().isEmpty()) {
			query.append("WITH DISTINCT m as visibles").append(pcr);
			query.append(customReturn);
		} else {
			query.append("RETURN distinct m.id as id, m.name as name, "
				+ "m.login as login, m.displayName as username, ").append(pr)
				.append("m.lastName as lastName, m.firstName as firstName "
				+ "ORDER BY name, username ");
		}
		params.put("userId", userId);
		if (additionnalParams != null) {
			params.putAll(additionnalParams);
		}
		neo.send(query.toString(), params, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> res) {
				JsonArray r = new JsonArray();
				if ("ok".equals(res.body().getString("status"))) {
					r = resultToJsonArray(res.body().getObject("result"));
				}
				handler.handle(r);
			}
		});
	}

	private void usersCanSeeMe(String userId, final Handler<JsonArray> handler) {
		String query =
				"MATCH p=(n:User)<-[:COMMUNIQUE*0..2]-t<-[r:COMMUNIQUE|COMMUNIQUE_DIRECT]-(m:User) " +
				"WHERE n.id = {userId} AND ((type(r) = 'COMMUNIQUE_DIRECT' AND length(p) = 1) " +
				"XOR (type(r) = 'COMMUNIQUE' AND length(p) >= 2)) AND m.id <> {userId} " +
				"OPTIONAL MATCH m-[:IN]->(pg:ProfileGroup)-[:HAS_PROFILE]->(profile:Profile) " +
				"RETURN distinct m.id as id, m.login as login, " +
				"m.displayName as username, profile.name as type " +
				"ORDER BY username ";
		Map<String, Object> params = new HashMap<>();
		params.put("userId", userId);
		neo.send(query, params, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> res) {
				JsonArray r = new JsonArray();
				if ("ok".equals(res.body().getString("status"))) {
					r = resultToJsonArray(res.body().getObject("result"));
				}
				handler.handle(r);
			}
		});
	}

	private void visibleProfilsGroups(String userId, String customReturn, JsonObject additionnalParams,
			final Handler<JsonArray> handler) {
		String r;
		if (customReturn != null && !customReturn.trim().isEmpty()) {
			r = "WITH gp as profileGroup, profile " + customReturn;
		} else {
			r = "RETURN distinct gp.id as id, gp.name as name, profile.name as type " +
				"ORDER BY type DESC, name ";
		}
		Map<String, Object> params =
				(additionnalParams != null) ? additionnalParams.toMap() : new HashMap<String, Object>();
		params.put("userId", userId);
		String query =
				"MATCH (n:User)-[:COMMUNIQUE*1..2]->l<-[:DEPENDS*0..1]-(gp:ProfileGroup) " +
				"WHERE n.id = {userId} " +
				"OPTIONAL MATCH gp-[:DEPENDS*0..1]->(pg:ProfileGroup)-[:HAS_PROFILE]->(profile:Profile) " +
				r;
		neo.send(query, params, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> res) {
				JsonArray r = new JsonArray();
				if ("ok".equals(res.body().getString("status"))) {
					r = resultToJsonArray(res.body().getObject("result"));
				}
				handler.handle(r);
			}
		});
	}

	private void usersInProfilGroup(String profilGroupId, boolean itSelf, String userId,
			final Handler<JsonArray> handler) {
		String condition = (itSelf || userId == null) ? "" : "AND u.id <> {userId} ";
		String query =
				"MATCH (n:ProfileGroup)<-[:IN]-(u:User) " +
				"WHERE n.id = {groupId} " + condition +
				"OPTIONAL MATCH n-[:DEPENDS*0..1]->(pg:ProfileGroup)-[:HAS_PROFILE]->(profile:Profile) " +
				"RETURN distinct u.id as id, u.login as login," +
				" u.displayName as username, profile.name as type " +
				"ORDER BY username ";
		Map<String, Object> params = new HashMap<>();
		params.put("groupId", profilGroupId);
		if (!itSelf && userId != null) {
			params.put("userId", userId);
		}
		neo.send(query, params, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
				JsonArray r = new JsonArray();
				if ("ok".equals(res.body().getString("status"))) {
					r = resultToJsonArray(res.body().getObject("result"));
				}
				handler.handle(r);
			}
		});
	}

	private JsonArray resultToJsonArray(JsonObject j) {
		JsonArray r = new JsonArray();
		for (String idx : j.getFieldNames()) {
			r.addObject(j.getObject(idx));
		}
		return r;
	}

	@SecuredAction("communication.default.rules")
	public void defaultCommunicationRules(final HttpServerRequest request) {
		String schoolId = request.params().get("schoolId");
		if (schoolId == null || schoolId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		setDefaultCommunicationRules(new JsonArray().add(schoolId), new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> r) {
				if ("ok".equals(r.body().getString("status"))) {
					renderJson(request, r.body());
				} else {
					renderError(request, r.body());
				}
			}
		});
	}

	private void setDefaultCommunicationRules(
			JsonArray schoolIds, Handler<Message<JsonObject>> handler) {
		JsonArray r = container.config().getArray("defaultCommunicationRules");
		if (r == null || r.size() == 0 || schoolIds == null || schoolIds.size() == 0) {
			handler.handle(null);
			return;
		}
		StatementsBuilder b = new StatementsBuilder();
		for (Object s : schoolIds) {
			if (!(s instanceof String)) continue;
			String schoolId = (String) s;
			final JsonObject params = new JsonObject().putString("schoolId", schoolId);
			for (Object o: r) {
				if (!(o instanceof String) || ((String) o).trim().isEmpty()) continue;
				if (((String) o).contains("RELATED")) {
					b.add("MATCH " + o + " CREATE UNIQUE start-[:COMMUNIQUE_DIRECT]->end", params);
				} else {
					if (((String) o).contains("startStructureGroup") && ((String) o).contains("endStructureGroup")) {
						b.add(
							"MATCH (s:Structure)<-[:DEPENDS]-(startStructureGroup:ProfileGroup)" +
							"-[:HAS_PROFILE]-(startProfile:Profile), " +
							"s<-[:DEPENDS]-(endStructureGroup:ProfileGroup)-[:HAS_PROFILE]-(endProfile:Profile) " +
							"WHERE s.id = {schoolId} " + o +
							"CREATE UNIQUE start-[:COMMUNIQUE]->end", params
						);
					} else if (((String) o).contains("endProfile")) {
						b.add(
							"MATCH (s:Structure)<-[:BELONGS]-(c:Class), " +
							"c<-[:DEPENDS]-(startClassGroup:ProfileGroup)-[:DEPENDS]->" +
							"(startStructureGroup:ProfileGroup)-[:HAS_PROFILE]-(startProfile:Profile), " +
							"c<-[:DEPENDS]-(endClassGroup:ProfileGroup)-[:DEPENDS]->" +
							"(endStructureGroup:ProfileGroup)-[:HAS_PROFILE]-(endProfile:Profile) " +
							"WHERE s.id = {schoolId} " + o +
							"CREATE UNIQUE start-[:COMMUNIQUE]->end", params
						);
					} else if (((String) o).contains("userClass")) {
						b.add(
							"MATCH (s:Structure)<-[:BELONGS]-(c:Class), " +
							"c<-[:DEPENDS]-(classGroup:ProfileGroup)-[:DEPENDS]->" +
							"(structureGroup:ProfileGroup)-[:HAS_PROFILE]-(profile:Profile), " +
							"classGroup<-[:IN]-(userClass:User) " +
							"WHERE s.id = {schoolId} " + o +
							"CREATE UNIQUE start-[:COMMUNIQUE]->end", params
						);
					} else if (((String) o).contains("userStructure")) {
						b.add(
							"MATCH (s:Structure)<-[:DEPENDS]-(structureGroup:ProfileGroup)" +
							"-[:HAS_PROFILE]-(profile:Profile), " +
							"structureGroup<-[:IN]-(userStructure:User) " +
							"WHERE s.id = {schoolId} " + o +
							"CREATE UNIQUE start-[:COMMUNIQUE]->end", params
						);
					}
				}
			}
			b.add(
				"MATCH (s:Structure)<-[:DEPENDS*1..2]-(g:Group)<-[:IN*0..1]-(v), v<-[:COMMUNIQUE|COMMUNIQUE_DIRECT]-() " +
				"WHERE s.id = {schoolId} AND NOT(v:Visible) " +
				"WITH DISTINCT v " +
				"SET v:Visible ", params
			);
		}
		neo.executeTransaction(b.build(), null, true, handler);
	}

	public void communicationEventBusHandler(final Message<JsonObject> message) {
		switch (message.body().getString("action", "")) {
			case "setDefaultCommunicationRules" :
				setDefaultCommunicationRules(new JsonArray().add(
						message.body().getString("schoolId")), new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> r) {
						if (r != null) {
							message.reply(r.body());
						} else {
							message.reply(new JsonObject().putString("status", "error")
									.putString("message", "invalid.schoolId"));
						}
					}
				});
				break;
			case "setMultipleDefaultCommunicationRules" :
				setDefaultCommunicationRules(
						message.body().getArray("schoolIds"), new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> r) {
						if (r != null) {
							message.reply(r.body());
						} else {
							message.reply(new JsonObject().putString("status", "error")
									.putString("message", "invalid.schoolId"));
						}
					}
				});
				break;
			default:
				message.reply(new JsonObject().putString("status", "error")
						.putString("message", "invalid.action"));
		}
	}

	@SecuredAction("communication.remove.rules")
	public void removeCommunicationRules(HttpServerRequest request) {
		String schoolId = request.params().get("schoolId");
		if (schoolId == null || schoolId.trim().isEmpty()) {
			renderError(request);
			return;
		}
		String query =
				"MATCH (s:Structure)<-[:DEPENDS*1..2]-(g:ProfileGroup)-[r:COMMUNIQUE]-() " +
				"WHERE s.id = {schoolId} " +
				"OPTIONAl MATCH s<-[:BELONGS]-(c:Class)<-[:DEPENDS]-(pg:ProfileGroup)<-[:IN]" +
				"-(u:User)-[r1:COMMUNIQUE_DIRECT]->() " +
				"DELETE r, r1";
		neo.execute(query, new JsonObject().putString("schoolId", schoolId), request.response());
	}

}
