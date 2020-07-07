package it.unibo.arces.wot.sepa.apps.mqtt.mappers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.api.ISubscriptionHandler;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.response.Notification;
import it.unibo.arces.wot.sepa.commons.security.ClientSecurityManager;
import it.unibo.arces.wot.sepa.commons.sparql.ARBindingsResults;
import it.unibo.arces.wot.sepa.commons.sparql.Bindings;
import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermLiteral;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermURI;
import it.unibo.arces.wot.sepa.pattern.GenericClient;
import it.unibo.arces.wot.sepa.pattern.JSAP;

public abstract class MqttMapper implements ISubscriptionHandler {
	protected static final Logger logger = LogManager.getLogger();

	// Topics mapping
	protected static HashMap<String, String> topic2observation = new HashMap<String, String>();
	protected final HashMap<String, String> aliases = new HashMap<String, String>();

	protected ArrayList<String> topics = new ArrayList<String>();

	protected final String mapperUri;

	private boolean subscribed = false;
	
	private GenericClient client;
	private JSAP appProfile;
	
	public MqttMapper(ClientSecurityManager sm, String uri) throws SEPAProtocolException, SEPAPropertiesException, SEPASecurityException {
		this.appProfile = new JSAP("mqtt.jsap");
		
		client = new GenericClient(appProfile, sm, this);

		mapperUri = uri;
	}
		
	public MqttMapper(String uri) throws SEPAProtocolException, SEPAPropertiesException, SEPASecurityException {
		this.appProfile = new JSAP("mqtt.jsap");
		
		client = new GenericClient(appProfile, null, this);

		mapperUri = uri;
	}
	
	public void start() {
		while (!subscribed) {
			aliases.clear();
			try {
				subscribe(mapperUri);
			} catch (SEPAProtocolException | SEPASecurityException | SEPAPropertiesException | SEPABindingsException
					| InterruptedException e) {
				logger.error(mapperUri + " failed to subscribe");
				continue;
			}

			synchronized (this) {
				try {
					wait(5000);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	private void subscribe(String uri) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException,
			SEPABindingsException, InterruptedException {
		logger.info(mapperUri + " subscribe");

		new Thread() {
			public void run() {
				try {
					client.subscribe("MQTT_MESSAGES", null, null, "message");
				} catch (SEPAProtocolException | SEPASecurityException | SEPAPropertiesException | SEPABindingsException
						| InterruptedException e) {
					logger.error(e.getMessage());
					if (logger.isTraceEnabled()) e.printStackTrace();
				}
			}
		}.start();
		
		new Thread() {
			public void run() {
				try {
					client.subscribe("MQTT_MAPPINGS", null, null, "mappings");
				} catch (SEPAProtocolException | SEPASecurityException | SEPAPropertiesException | SEPABindingsException
						| InterruptedException e) {
					logger.error(e.getMessage());
					if (logger.isTraceEnabled()) e.printStackTrace();
				}
			}
		}.start();
		
		new Thread() {
			public void run() {
				try {
					if (!mapperUri.equals("mqtt:DefaultMapper")) {
						Bindings fb = new Bindings();
						fb.addBinding("mapper", new RDFTermURI(mapperUri));
						client.subscribe("MQTT_MAPPER", null, fb, "topics");
					} else {
						client.subscribe("MQTT_MAPPERS_TOPICS", null, null, "topics");
					}
				} catch (SEPAProtocolException | SEPASecurityException | SEPAPropertiesException | SEPABindingsException
						| InterruptedException e) {
					logger.error(e.getMessage());
					if (logger.isTraceEnabled()) e.printStackTrace();
				}
			}
		}.start();
	}

	protected abstract ArrayList<String[]> map(String topic, String value);

	private ArrayList<String[]> mapInternal(String topic, String value) {

		// Default mapping
		if (mapperUri.equals("mqtt:DefaultMapper")) {
			// Topic is registered to a specific mapper
			if (topics.contains(topic)) {
				logger.debug(mapperUri + " skip topic: " + topic);
				return null;
			}
		} else {
			// Topic should be managed by another mapper
			if (!topics.contains(topic)) {
				logger.debug(mapperUri + " skip topic: " + topic);
				return null;
			}
		}

		return map(topic, value);
	}

	@Override
	public final void onSemanticEvent(Notification notify) {
		ARBindingsResults results = notify.getARBindingsResults();

		BindingsResults added = results.getAddedBindings();
		BindingsResults removed = results.getRemovedBindings();

		if (aliases.get(notify.getSpuid()).equals("message")) {
			// Skip mapping previously stored messages
			if (notify.getSequence() == 0) {
				logger.debug(mapperUri + " skip previously stored messages");
				return;
			}

			for (Bindings bindings : added.getBindings()) {
				// ?topic ?value ?broker
				String topic = bindings.getValue("topic");
				String value = bindings.getValue("value");

				if (value == null || topic == null)
					continue;
				if (value.equals("NaN"))
					continue;

				ArrayList<String[]> observations = mapInternal(topic, value);

				if (observations == null) {
					// logger.debug("Mapping NOT FOUND: "+topic);
					continue;
				}

				for (String[] observation : observations) {
					Bindings fb = new Bindings();
					fb.addBinding("observation", new RDFTermURI(observation[0]));
					fb.addBinding("value", new RDFTermLiteral(observation[1],
							appProfile.getUpdateBindings("UPDATE_OBSERVATION_VALUE").getDatatype("value")));

					try {
						client.update("UPDATE_OBSERVATION_VALUE", fb);
					} catch (SEPASecurityException | IOException | SEPAPropertiesException | SEPABindingsException
							| SEPAProtocolException e) {
						logger.error(mapperUri + " " + e.getMessage());
					}
				}
			}
		} else if (aliases.get(notify.getSpuid()).equals("mappings")) {
			for (Bindings bindings : removed.getBindings()) {
				topic2observation.remove(bindings.getValue("topic"));
				logger.debug(mapperUri + " removed mappings: " + bindings.getValue("topic"));
			}
			for (Bindings bindings : added.getBindings()) {
				topic2observation.put(bindings.getValue("topic"), bindings.getValue("observation"));
				logger.debug(mapperUri + " added mappings: " + bindings.getValue("topic"));
			}
		} else if (aliases.get(notify.getSpuid()).equals("topics")) {
			for (Bindings bindings : removed.getBindings()) {
				topics.remove(bindings.getValue("topic"));
				logger.info(mapperUri + " remove topic: " + bindings.getValue("topic"));
			}

			for (Bindings bindings : added.getBindings()) {
				if (topics.contains(bindings.getValue("topic")))
					continue;
				topics.add(bindings.getValue("topic"));
				logger.info(mapperUri + " add topic: " + bindings.getValue("topic"));
			}

		}
	}

	@Override
	public void onBrokenConnection() {
		logger.error(mapperUri + " *** onBrokenConnection ***");

		while (!subscribed) {
			logger.info(mapperUri + " subscribe...");
			aliases.clear();
			try {
				subscribe(mapperUri);
			} catch (SEPAProtocolException | SEPASecurityException | SEPAPropertiesException | SEPABindingsException
					| InterruptedException e) {
				logger.error(mapperUri + " failed to subscribe. " + e.getMessage());
				continue;
			}

			synchronized (this) {
				try {
					wait(5000);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	@Override
	public void onError(ErrorResponse errorResponse) {
		logger.error(mapperUri + " " + errorResponse);
	}

	@Override
	public void onSubscribe(String spuid, String alias) {
		logger.info(mapperUri + " SPUID: " + spuid + " alias: " + alias);
		if (alias != null) {
			aliases.put(spuid, alias);
			if (aliases.size() == 3) {
				subscribed = true;
				logger.info(mapperUri + " *** SUBSCRIBED ***");
			}
		}
	}

	@Override
	public void onUnsubscribe(String spuid) {
		logger.info(mapperUri + " unsubscribed. SPUID: " + spuid);
	}
}
