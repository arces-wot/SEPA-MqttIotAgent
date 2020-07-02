package it.unibo.arces.wot.sepa.apps.mqtt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.logging.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.security.ClientSecurityManager;
import it.unibo.arces.wot.sepa.commons.sparql.Bindings;
import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermLiteral;
import it.unibo.arces.wot.sepa.pattern.Aggregator;
import it.unibo.arces.wot.sepa.pattern.JSAP;

public class MqttAdapter extends Aggregator implements MqttCallbackExtended {
	private static final Logger logger = LogManager.getLogger();

	private MqttClient mqttClient;
	private String serverURI;
	private String serverUID;
	private ArrayList<String> topics = new ArrayList<String>();
	private ArrayList<String> topicsRemoved = new ArrayList<String>();

	private MqttConnectOptions options;

	private Thread subThread;
	private Thread unsubThread;
	private Thread connThread;

	/**
	 * The application assumes the ID of the adapter as argument. The adapter is selected from the JSAP as follows:
	 * 
	 * "extended": {
		"adapters": {
			"mqtt": {
				"ID" : 	{
					"url": "boswamp-2.arces.unibo.it",
					"port": 8883,
					"topics": ["transmission_example/devices/new_device/up"],
					"sslProtocol": "SSL",
					"caFile": "client_swamp.crt",
					"username": "swamp",
					"password": "arces"
				}
			}
		}
	 }
	 * 
	 * 
	 * */
	public static void main(String[] args) throws SEPASecurityException, InterruptedException, SEPAProtocolException,
			SEPAPropertiesException, SEPABindingsException, IOException {

		if (args.length != 1) {
			logger.error("Missing arguments: adapter ID");
			return;
		}

		JSAP jsap = new JSAP("mqtt.jsap");
		
		ClientSecurityManager security = (jsap.isSecure() ? new ClientSecurityManager(jsap.getAuthenticationProperties(), "sepa.jks",
				"sepa2017"): null);

		// Load adapter
		jsap.read("adapters.jsap");
		JsonObject config =  jsap.getExtendedData().getAsJsonObject("adapters").getAsJsonObject("mqtt").getAsJsonObject(args[0]);
		
		String url = config.get("url").getAsString();
		int port = config.get("port").getAsInt();
		
		String user = (config.has("username") ? config.get("username").getAsString() : null);
		String password = (config.has("password") ? config.get("password").getAsString() : null);	
		String clientId = (config.has("clientId") ? config.get("clientId").getAsString() : null);
		String caCertFile = (config.has("caFile") ? config.get("caFile").getAsString() : null);
		String protocol = (config.has("sslProtocol") ? config.get("sslProtocol").getAsString() : null);
		
		MqttAdapter clientAdapter = new MqttAdapter(jsap, security, url, port, clientId, user, password, protocol,
				caCertFile);

		JsonArray topics = (config.has("topics") ? config.get("topics").getAsJsonArray() : null);
		if (topics == null) clientAdapter.subscribe("#");
		else {
			for (JsonElement topic : topics) {
				clientAdapter.subscribe(topic.getAsString());
			}
		}

		synchronized (clientAdapter) {
			clientAdapter.wait();
		}
		clientAdapter.close();
	}

	public void enableMqttDebugging() {
		LoggerFactory.setLogger(Logging.class.getName());
	}

	public MqttAdapter(JSAP appProfile, ClientSecurityManager sm, JsonObject sim)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException {
		super(appProfile, "MQTT_BROKER_TOPICS", "MQTT_MESSAGE", sm);

		simulator(sim);
	}

	public void subscribe(String topic) {
		synchronized (topics) {
			topics.add(topic);
			topics.notify();
		}
	}

	public void unsubscribe(String topic) {
		synchronized (topicsRemoved) {
			topicsRemoved.add(topic);
			topicsRemoved.notify();
		}
	}

	public MqttAdapter(JSAP appProfile, ClientSecurityManager sm, String url, int port, String clientId, String user,
			String password, String protocol, String caCertFile)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException {
		super(appProfile, "MQTT_BROKER_TOPICS", "MQTT_MESSAGE", sm);

		// Options
		options = new MqttConnectOptions();
		options.setAutomaticReconnect(true);

		logger.debug("Client ID: " + clientId);

		if (protocol != null) {
			logger.info("SSL enabled");

			serverURI = String.format("ssl://%s:%d", url, port);

			// CA certificates
			if (caCertFile == null)
				options.setSocketFactory(SSL.getSSLContextTrustAllCa(protocol).getSocketFactory());
			else
				options.setSocketFactory(SSL.getSSLContextFromCertFile(protocol, caCertFile).getSocketFactory());

		} else {
			serverURI = String.format("tcp://%s:%d", url, port);
		}

		serverUID = serverURI + "@" + user;

		logger.info("Connect MQTT client to broker: " + serverUID);

		// Create client
		logger.debug("Creating MQTT client...");
		if (clientId != null) {

			try {
				mqttClient = new MqttClient(serverURI, clientId);
			} catch (MqttException e) {
				throw new SEPASecurityException(e);
			}
		} else
			try {
				mqttClient = new MqttClient(serverURI, MqttClient.generateClientId());
			} catch (MqttException e) {
				throw new SEPASecurityException(e);
			}

		if (user != null) {
			logger.debug("User: " + user);
			options.setUserName(user);
		}

		if (password != null) {
			logger.debug("Password: " + password);
			options.setPassword(password.toCharArray());
		}

		mqttClient.setCallback(this);

		connThread = new Thread() {
			public void run() {
				while (!this.isInterrupted()) {
					try {
						logger.debug(serverUID + " connecting...");
						mqttClient.connect(options);
						break;
					} catch (MqttSecurityException e) {
						logger.error(serverUID + " MqttSecurityException: " + e.getMessage());
					} catch (MqttException e) {
						logger.error(serverUID + " MqttException: " + e.getMessage());
					}
				}
			}
		};
		connThread.setName(serverURI + "@" + user + " conn");
		connThread.start();

		subThread = new Thread() {
			public void run() {
				while (!this.isInterrupted()) {
					synchronized (topicsRemoved) {
						try {
							logger.debug(serverUID + " wait for topics");
							topicsRemoved.wait();
						} catch (InterruptedException e) {
							return;
						}

						for (String topic : topicsRemoved) {
							while (true) {

								try {
									logger.debug(serverUID + " unsubscribe from: " + topic);
									mqttClient.unsubscribe(topic);
									topics.remove(topic);
								} catch (MqttException e) {
									try {
										Thread.sleep(500);
									} catch (InterruptedException e1) {
										return;
									}
									logger.warn(serverUID + " exception on unsubscribe: " + e.getMessage());
									continue;
								}

								logger.debug(serverUID + " unsubscribed from: " + topic);

								break;
							}
						}

						topicsRemoved.clear();
					}
				}
			}
		};
		subThread.setName(serverUID + " sub");
		subThread.start();

		unsubThread = new Thread() {
			public void run() {
				while (true) {
					synchronized (topics) {
						try {
							logger.debug(serverUID + " wait for topics");
							topics.wait();
						} catch (InterruptedException e) {
							return;
						}

						for (String topic : topics) {
							while (true) {
								try {
									logger.debug(serverUID + " subscribe to " + topic);
									mqttClient.subscribe(topic);
								} catch (MqttException e) {
									try {
										Thread.sleep(500);
									} catch (InterruptedException e1) {
										return;
									}
									logger.warn(serverUID + " exception on subscribe: " + e.getMessage());
									continue;
								}

								logger.info(serverUID + " subscribed to: " + topic);

								break;
							}
						}
					}

				}
			}
		};
		unsubThread.setName(serverUID + " unsub");
		unsubThread.start();

		try {
			setSubscribeBindingValue("url", new RDFTermLiteral(url));
			setSubscribeBindingValue("port", new RDFTermLiteral(String.format("%d", port), "xsd:integer"));
			if (user != null)
				setSubscribeBindingValue("user", new RDFTermLiteral(user));
			else
				setSubscribeBindingValue("user", new RDFTermLiteral(""));
		} catch (SEPABindingsException e1) {
			logger.error(serverUID + " failed to set bindings: " + e1.getMessage());
		}

		try {
			logger.debug(serverUID + " subscribe to SEPA...");
			subscribe(5000);
		} catch (SEPASecurityException | SEPAPropertiesException | SEPAProtocolException | SEPABindingsException e) {
			logger.error(serverUID + " failed to subscribe: " + e.getMessage());
		}
	}

	public void simulator(JsonObject topics) {
		new Thread() {
			public void run() {
				while (true) {
					for (Entry<String, JsonElement> observation : topics.entrySet()) {
						String topic = observation.getKey();
						int min = observation.getValue().getAsJsonArray().get(0).getAsInt();
						int max = observation.getValue().getAsJsonArray().get(1).getAsInt();
						String value = String.format("%.2f", min + (Math.random() * (max - min)));

						logger.info("[Simulate MQTT message] Topic: " + topic + " Value: " + value);

						try {
							setUpdateBindingValue("topic", new RDFTermLiteral(topic));
							setUpdateBindingValue("value", new RDFTermLiteral(value));
							setUpdateBindingValue("broker", new RDFTermLiteral("simulator"));

//							OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);			
//							setUpdateBindingValue("timestamp", new RDFTermLiteral(utc.toString()));

							update();
						} catch (SEPASecurityException | SEPAProtocolException | SEPAPropertiesException
								| SEPABindingsException e) {
							logger.error(e.getMessage());
						}

						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							return;
						}
					}
				}
			}
		}.start();
	}

	@Override
	public void connectComplete(boolean reconnect, String serverURI) {
		logger.info(serverUID + " @connectComplete reconnect: " + reconnect);

		synchronized (topics) {
			logger.debug(serverUID + " notify!");
			topics.notify();
		}
	}

	@Override
	public void connectionLost(Throwable cause) {
		logger.warn(serverUID + " connection lost: " + cause.getMessage());
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) {
		logger.debug("Topic: " + topic + " Message: " + message);

		String converted = "";
		try {
			byte[] payload = message.getPayload();

			for (int i = 0; i < payload.length; i++) {
				if (payload[i] == 0)
					break;
				converted += String.format("%c", payload[i]);
			}

			logger.info(serverUID + " message received: " + topic + " " + converted);
		} catch (IllegalFormatException e) {
			logger.warn(e.getMessage());
			return;
		}

		try {
//			OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);

			setUpdateBindingValue("topic", new RDFTermLiteral(topic));
			setUpdateBindingValue("value", new RDFTermLiteral(converted));
			setUpdateBindingValue("broker", new RDFTermLiteral(serverURI));
//			setUpdateBindingValue("timestamp", new RDFTermLiteral(utc.toString(),"xsd:dateTime"));

			Response ret = update();

			if (ret.isError()) {
				logger.error("Failed to update MQTT message: " + ret);
			} else {
				logger.debug("Update response: " + ret);
			}

		} catch (SEPASecurityException | SEPAProtocolException | SEPAPropertiesException | SEPABindingsException e) {
			logger.error(serverUID + " exception:" + e.getMessage());
		}
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {

	}

	@Override
	public void close() throws IOException {
		super.close();

		if (subThread != null)
			if (subThread.isAlive())
				subThread.interrupt();
		if (unsubThread != null)
			if (unsubThread.isAlive())
				unsubThread.interrupt();
		if (connThread != null)
			if (connThread.isAlive())
				connThread.interrupt();

		try {
			mqttClient.disconnect();
		} catch (MqttException e) {
			logger.warn(serverUID + " failed to disconnect " + e.getMessage());
		}

		try {
			mqttClient.close();
		} catch (MqttException e) {
			logger.warn(serverUID + " failed to close " + e.getMessage());
		}
	}

	@Override
	public void onAddedResults(BindingsResults results) {
		synchronized (topics) {
			for (Bindings bindings : results.getBindings()) {
				String topicString = bindings.getValue("topic");

				if (!topics.contains(topicString)) {
					logger.debug("Added topic: " + topicString);
					topics.add(topicString);
				}
			}

			logger.debug(serverUID + " notify!");
			topics.notify();
		}
	}

	@Override
	public void onRemovedResults(BindingsResults results) {
		synchronized (topicsRemoved) {
			for (Bindings bindings : results.getBindings()) {
				String topicString = bindings.getValue("topic");

				if (!topicsRemoved.contains(topicString)) {
					logger.debug("Removed topic: " + topicString);
					topicsRemoved.add(topicString);
				}
			}

			logger.debug(serverUID + " notify!");
			topicsRemoved.notify();
		}
	}

	@Override
	public void onFirstResults(BindingsResults results) {
		onAddedResults(results);
	}
}
