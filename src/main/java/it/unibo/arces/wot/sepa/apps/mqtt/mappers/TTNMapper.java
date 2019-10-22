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
{
"payload_fields":{
	"battery":85,
	"sensor_1":{"Conductivity":1,"Permittivity":124.05,"Temperature":22.7},
	"sensor_3":{"Conductivity":1,"Permittivity":0.52,"Temperature":23.1},
	"sensor_6":{"Conductivity":1,"Permittivity":0.52,"Temperature":22.8}}
}
 * */
public class TTNMapper extends MqttMapper {

	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException, IOException{			
		TTNMapper mapper = new TTNMapper(new JSAP("mqtt.jsap"),null);
		
		synchronized(mapper) {
			mapper.wait();
		}
		
		mapper.close();		
	}
	
	public TTNMapper(JSAP appProfile, SEPASecurityManager sm)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException {
		super(appProfile, sm, "mqtt:MeterMapper");
	}

	@Override
	protected ArrayList<String[]> map(String topic, String value) {
		ArrayList<String[]> mapping = new ArrayList<String[]>();
		
		JsonObject json = new JsonParser().parse(value).getAsJsonObject();
		
		if (!json.has("payload_fields")) {
			logger.error("payload_fields member not found: "+value);
			return mapping;
		}
		
		json = json.getAsJsonObject("payload_fields");
		
		for (Entry<String, JsonElement> elem : json.entrySet()) {
			String t = null;
			String v = null;
			
			if (elem.getKey().equals("battery")) {
				t = topic+"/battery";
				v = String.format("%d", elem.getValue().getAsInt());
			}
			else if (elem.getKey().startsWith("sensor")) {
				JsonObject sensor = json.get(elem.getKey()).getAsJsonObject();
				for (Entry<String, JsonElement> sens : sensor.entrySet()) {
					t = topic+"/"+elem.getKey()+"/"+sens.getKey();
					v = String.format("%.2f", sens.getValue().getAsFloat());					
				}
			}
			
			if (t == null) {
				logger.warn("Topic: "+topic+" Failed to parse: "+elem.getKey());
				continue;
			}
			
			String observation = topic2observation.get(t);
			
			if (observation == null) {
				logger.warn("Topic: "+t+" MAPPING NOT FOUND");
				continue;
			}
			
			mapping.add(new String[] {observation,v});
		}
		
		return mapping;
	}

}
