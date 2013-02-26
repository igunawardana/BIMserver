package org.bimserver.servlets;

/******************************************************************************
 * Copyright (C) 2009-2013  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

import java.io.IOException;
import java.io.StringReader;
import java.util.GregorianCalendar;

import org.apache.commons.io.output.NullWriter;
import org.bimserver.BimServer;
import org.bimserver.endpoints.EndPoint;
import org.bimserver.models.log.AccessMethod;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.shared.interfaces.NotificationInterface;
import org.bimserver.shared.interfaces.RemoteServiceInterface;
import org.codehaus.jettison.json.JSONException;
import org.eclipse.jetty.websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;

public class StreamingSocket implements WebSocket.OnTextMessage, EndPoint, StreamingSocketInterface {

	private static final Logger LOGGER = LoggerFactory.getLogger(StreamingSocket.class);
	private Connection connection;
	private BimServer bimServer;
	private long uoid;
	private long endpointid;
	private NotificationInterface notificationInterface;
	private RemoteServiceInterface remoteServiceInterface;

	public StreamingSocket(BimServer bimServer) {
		this.bimServer = bimServer;
		this.notificationInterface = bimServer.getReflectorFactory().createReflector(NotificationInterface.class, new JsonWebsocketReflector(bimServer.getServicesMap(), this));
		this.remoteServiceInterface = bimServer.getReflectorFactory().createReflector(RemoteServiceInterface.class, new JsonWebsocketReflector(bimServer.getServicesMap(), this));
	}

	@Override
	public void onClose(int arg0, String arg1) {
		bimServer.getEndPointManager().unregister(this);
	}

	@Override
	public void onOpen(Connection connection) {
		this.connection = connection;
		JsonObject welcome = new JsonObject();
		welcome.add("welcome", new JsonPrimitive(new GregorianCalendar().getTimeInMillis()));
		send(welcome);
	}

	@Override
	public void onMessage(String message) {
		try {
			JsonReader reader = new JsonReader(new StringReader(message));
			JsonParser parser = new JsonParser();
			JsonObject request = (JsonObject) parser.parse(reader);
			if (request.has("token")) {
				String token = request.get("token").getAsString();
				try {
					org.bimserver.shared.interfaces.ServiceInterface service = bimServer.getServiceFactory().getService(org.bimserver.shared.interfaces.ServiceInterface.class, token, AccessMethod.JSON);
					uoid = service.getCurrentUser().getOid();

					this.endpointid = bimServer.getEndPointManager().register(this);
					
					JsonObject enpointMessage = new JsonObject();
					enpointMessage.add("endpointid", new JsonPrimitive(endpointid));
					send(enpointMessage);
				} catch (UserException e) {
					e.printStackTrace();
				} catch (ServerException e) {
					e.printStackTrace();
				}
			} else {
				bimServer.getJsonHandler().execute(request, null, new NullWriter());
			}
		} catch (JSONException e) {
			LOGGER.error("", e);
		} catch (IOException e) {
			LOGGER.error("", e);
		}
	}

	public void send(JsonObject object) {
		try {
//			LOGGER.info("sending " + object.toString(2));
			connection.sendMessage(object.toString());
		} catch (IOException e) {
		}
	}

	@Override
	public long getEndPointId() {
		return endpointid;
	}

	@Override
	public NotificationInterface getNotificationInterface() {
		return notificationInterface;
	}

	public RemoteServiceInterface getRemoteServiceInterface() {
		return remoteServiceInterface;
	}
	
	@Override
	public void cleanup() {
		bimServer.getEndPointManager().unregister(endpointid);
	}
	
	@Override
	public long getUoid() {
		return uoid;
	}
}