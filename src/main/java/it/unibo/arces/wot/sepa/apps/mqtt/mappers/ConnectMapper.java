package it.unibo.arces.wot.sepa.apps.mqtt.mappers;

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;

public class ConnectMapper extends MqttMapper {

	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException, IOException{			
		ConnectMapper mapper = new ConnectMapper();
		
		mapper.start();
		
		synchronized(mapper) {
			mapper.wait();
		}	
	}
	
	public ConnectMapper() throws SEPAProtocolException, SEPAPropertiesException, SEPASecurityException {
		super("mqtt:ConnectMapper");
	}

	@Override
	protected ArrayList<String[]> map(String topic, String value) {
		ArrayList<String[]> ret = new ArrayList<String[]>();

		// { "Temperature" : 26.8, "Humidity": 45.5 }
		JsonObject json = JsonParser.parseString(value).getAsJsonObject();

		String temp = topic2observation.get(topic + "/Temperature");
		if (temp == null) {
			logger.warn(mapperUri + " MAPPING NOT FOUND FOR TOPIC: " + topic + "/Temperature");
			return ret;
		}

		String hum = topic2observation.get(topic + "/Humidity");
		if (hum == null) {
			logger.warn(mapperUri + " MAPPING NOT FOUND FOR TOPIC: " + topic + "/Humidity");
			return ret;
		}

		try {
			float n = json.get("Temperature").getAsFloat();
			ret.add(new String[] {temp,String.format("%.2f", n)});
		} catch (Exception e) {
			logger.error(mapperUri + " WRONG JSON FORMAT (Temperature) " + topic + " "+e.getMessage());
		}
		
		try {
			float n = json.get("Humidity").getAsFloat();
			ret.add(new String[] {hum,String.format("%.2f", n)});
		} catch (Exception e) {
			logger.error(mapperUri + " WRONG JSON FORMAT (Humidity) " + topic + " "+e.getMessage());
		}
		
		return ret;
	}

}
