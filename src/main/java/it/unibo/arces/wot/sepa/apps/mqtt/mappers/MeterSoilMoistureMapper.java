package it.unibo.arces.wot.sepa.apps.mqtt.mappers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.security.SEPASecurityManager;
import it.unibo.arces.wot.sepa.pattern.JSAP;

/**
 * {
  "battery": 0,
  "sensor1": {
    "Conductivity": 86.02,
    "Permittivity": 17.34,
    "Temperature": 51.68
  },
  "sensor2": {
    "Conductivity": 86.02,
    "Permittivity": 17.34,
    "Temperature": 51.68
  },
  "sensor3": {
    "Conductivity": 86.02,
    "Permittivity": 17.34,
    "Temperature": 51.68
  },
  "sensor4": {
    "Conductivity": 86.02,
    "Permittivity": 17.34,
    "Temperature": 51.68
  },
  "sensor5": {
    "Conductivity": 86.02,
    "Permittivity": 17.34,
    "Temperature": 51.68
  },
  "sensor6": {
    "Conductivity": 86.02,
    "Permittivity": 17.34,
    "Temperature": 51.68
  }
}
 * 
 * */
public class MeterSoilMoistureMapper extends MqttMapper {

	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException, IOException{			
		MeterSoilMoistureMapper mapper = new MeterSoilMoistureMapper(new JSAP("mqtt.jsap"),null);
		
		synchronized(mapper) {
			mapper.wait();
		}
		
		mapper.close();		
	}
	
	public MeterSoilMoistureMapper(JSAP appProfile, SEPASecurityManager sm)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException {
		super(appProfile, sm, "mqtt:MeterMapper");
	}

	@Override
	protected ArrayList<String[]> map(String topic, String value) {
		ArrayList<String[]> mapping = new ArrayList<String[]>();
		
		JsonObject json = new JsonParser().parse(value).getAsJsonObject();
		
		for (Entry<String, JsonElement> elem : json.entrySet()) {
			if (elem.getKey().equals("battery")) {
				mapping.add(new String[] {topic+"/battery",String.format("%d", elem.getValue().getAsInt())});
			}
			else if (elem.getKey().startsWith("sensor")) {
				JsonObject sensor = json.get(elem.getKey()).getAsJsonObject();
				for (Entry<String, JsonElement> observation : sensor.entrySet()) { 
					mapping.add(new String[] {topic+"/"+elem.getKey()+"/"+observation.getKey(),String.format("%.2f", observation.getValue().getAsFloat())});
				}
			}
		}
		
		return mapping;
	}

}
