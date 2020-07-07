package it.unibo.arces.wot.sepa.apps.mqtt.mappers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.security.ClientSecurityManager;

/**
 * Guaspari soil moisture sensors
 * 
 * application/1/device/1bc0f73caf72d467/rx
 * {"applicationID":"1","applicationName":"Guaspari","deviceName":"EndNode1002","devEUI":"1bc0f73caf72d467","txInfo":{"frequency":916400000,"dr":4},"adr":false,"fCnt":4119,"fPort":1,"data":"U3wxOTA1MTMwNzEwfEl8MTAwMnxIMXwzMzh8SDJ8MzMxfEgzfDMzMQ=="}
 * application/1/device/754366e02ff23515/rx
 * {"applicationID":"1","applicationName":"Guaspari","deviceName":"EndNode1001","devEUI":"754366e02ff23515","txInfo":{"frequency":915200000,"dr":4},"adr":false,"fCnt":3338,"fPort":1,"data":"U3wxOTA2MTIxMzMwfEl8MTAwMXxIMXwzMzZ8SDJ8MzMzfEgzfDMzMg=="}
 * 
 
 application/1/device/0012f80000000641/rx 
 {"applicationID":"1","applicationName":"Guaspari","deviceName":"EndNode1003","devEUI":"0012f80000000641","txInfo":{"frequency":916400000,"dr":5},"adr":true,"fCnt":8556,"fPort":1,"data":"U3wyMDAxMzAwNzIwfEl8MTAwM3xIMXw0Mzh8SDJ8Mzg3fEgzfDMwOHxUMXwyNjU="}
 
  
 * mosquitto_sub --host 177.104.61.17 --port 1883 --verbose --topic '#'
 * 
 * 1) 10 mins ==> moisture S|1905130630|I|1002|H1|338|H2|331|H3|331
 * S|1905270910|I|1001|H1|338|H2|334|H3|333
 * 
 * 2) 1 hour ==> temperature S|timestamp|I|<ID>|T0|<hw temperature>|
 * 
 * S|1905270900|I|1001|T0|358|B|4094
 * 
 * mV ==> soil moisture °C ==> temperature
 * 
 * {"applicationID":"1","applicationName":"Guaspari","deviceName":"EndNode1002","devEUI":"1bc0f73caf72d467","txInfo":{"frequency":916400000,"dr":4},"adr":false,"fCnt":8836,"fPort":1,"data":"U3wxOTA2MjQwMDIwfEl8MTAwMnxIMXwzMzh8SDJ8MzMzfEgzfDMzMQ=="}
 *
 * Message format as 30-10-2019
 * 
 * application/1/device/0012f80000000614/rx {"applicationID":"1","applicationName":"Guaspari","deviceName":"EndNode1001","devEUI":"0012f80000000614","txInfo":{"frequency":915600000,"dr":2},"adr":true,"fCnt":11856,"fPort":1,"data":"U3wxOTEwMzAxMzEwfEl8MTAwMXxIMXw0NjN8SDJ8MzQ0fEgzfDMyMHxUMXwyNjg="}
 * application/1/device/0012f80000000615/rx {"applicationID":"1","applicationName":"Guaspari","deviceName":"EndNode1002","devEUI":"0012f80000000615","txInfo":{"frequency":915600000,"dr":5},"adr":true,"fCnt":16203,"fPort":1,"data":"U3wxOTEwMzAxMzEwfEl8MTAwMnxIMXwyODl8SDJ8Mzk0fEgzfDUyNHxUMXwzMTg="}
 * 
 * S|1910301230|I|1002|H1|289|H2|393|H3|524|T1|313 
 *
 */
public class GuaspariMapper extends MqttMapper {
	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException, IOException{			
		GuaspariMapper mapper = new GuaspariMapper();
		
		mapper.start();
		
		synchronized(mapper) {
			mapper.wait();
		}
	}
	
	public GuaspariMapper(ClientSecurityManager sm)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException {
		super(sm, "mqtt:GuaspariMapper");
	}
	
	public GuaspariMapper()
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException {
		super("mqtt:GuaspariMapper");
	}
	
	@Override
	protected ArrayList<String[]> map(String topic, String value) {
		ArrayList<String[]> ret = new ArrayList<String[]>();

		logger.debug(mapperUri+" Mapping: "+topic+" "+value);
		
		JsonObject json = JsonParser.parseString(value).getAsJsonObject();

		// Topic
		String newTopic = topic + "/" + json.get("deviceName").getAsString();

		// Payload
		byte[] decoded = Base64.getDecoder().decode(json.get("data").getAsString());
		String payload = new String(decoded);

		String[] tokens = payload.split("\\|");
		for (int i=0; i < tokens.length ; i = i + 2) {
			if (tokens[i].equals("S")) continue;
			if (tokens[i].equals("I")) continue;
			
			String observation = topic2observation.get(newTopic + "/" + tokens[i]);
			
			if (observation == null) {
				logger.warn(mapperUri+" Observation NOT FOUND: " + newTopic + "/" + tokens[i]);
			} else {
				// Parsing value
				String newValue = null;
				if (tokens[i].startsWith("T")) {
					newValue = String.format("%.1f", Float.parseFloat(tokens[i+1]) / 10);
				}
				else {
					newValue = tokens[i+1];
					
				}
				ret.add(new String[] { observation, newValue });
				logger.debug(mapperUri+" NEW observation: "+observation+ " Value: "+newValue);
			}
		}

		return ret;
	}

}
