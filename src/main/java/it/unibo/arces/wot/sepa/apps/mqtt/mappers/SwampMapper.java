package it.unibo.arces.wot.sepa.apps.mqtt.mappers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.security.ClientSecurityManager;

/**

{"applicationID":"1","applicationName":"MultiSensorNode_Application","deviceName":"MultiSensorNode_Bertacchini","devEUI":"00a76ec32f31ec32","rxInfo":[{"gatewayID":"c0ee40ffff29773c","uplinkID":"77e84da1-5681-44d2-a4ee-2687135e88e6","name":"SWAMP_Laird_Gateway","rssi":-60,"loRaSNR":10.2,"location":{"latitude":0,"longitude":0,"altitude":0}}],"txInfo":{"frequency":867700000,"dr":0},"adr":true,"fCnt":35,"fPort":2,"data":"LAF3AAoQFwoAgAACdwAKQRZQAE0AA3cACw4XCgEFAAZpAAIAGBQAAAAAAAo=",

"object":{"Battery":0,"Dutycycle_min":10,

"sensore_1":{"Conductivity":128,"Permittivity":10.16,"Temperature":23.1,"WaterContent":0.225},
"sensore_2":{"Conductivity":77,"Permittivity":10.65,"Temperature":22.8,"WaterContent":0.236},
"sensore_3":{"Conductivity":261,"Permittivity":11.14,"Temperature":23.1,"WaterContent":0.247},
"sensore_6":{"Conductivity":0,"FreezingFlag":0,"Temperature":24.2,"WaterDepth":2}}}
 
 {"applicationID":"4","applicationName":"Multisensors_node_Bertacchini","deviceName":"Multisensors_node_Bertacchini","devEUI":"00a76ec32f31ec32",
 "rxInfo":[{"gatewayID":"c0ee40ffff29773c","uplinkID":"4e98ab02-0f83-4e44-81a2-1009b913d845",
 "name":"SWAMP_Laird_Gateway","rssi":-94,"loRaSNR":9.2,"location":{"latitude":44.492357362833516,"longitude":11.330316066741945,"altitude":0}}],
 "txInfo":{"frequency":868100000,"dr":0},"adr":false,"fCnt":1022,"fPort":0,"data":null,"object":{}}
 * 
 * */
public class SwampMapper extends MqttMapper {

	private String entryMember = "object";
	
	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException, IOException{			
		SwampMapper mapper = new SwampMapper();
		
		mapper.start();
		
		synchronized(mapper) {
			mapper.wait();
		}
	}
	
	public SwampMapper(ClientSecurityManager sm)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException {
		super(sm, "mqtt:SwampMapper");
	}
	
	public SwampMapper()
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException {
		super("mqtt:SwampMapper");
	}

	@Override
	protected ArrayList<String[]> map(String topic, String value) {
		ArrayList<String[]> mapping = new ArrayList<String[]>();
		
		JsonObject json  = null;
		try{
			json = JsonParser.parseString(value).getAsJsonObject();
		}
		catch(JsonParseException e) {
			logger.error(mapperUri+" "+e.getMessage());
		}
		
		if (!json.has(entryMember)) {
			logger.error(mapperUri+" " + entryMember+" member not found: "+value);
			return mapping;
		}
		
		json = json.getAsJsonObject(entryMember);
		
		for (Entry<String, JsonElement> elem : json.entrySet()) {
		
			/*
			 * "sensore_****": {"measure1" : value1, "measure2" : value2}
			 * */
			if (elem.getKey().startsWith("sensore_")) {
				JsonObject sensor = json.get(elem.getKey()).getAsJsonObject();
				for (Entry<String, JsonElement> sens : sensor.entrySet()) {
					String t = topic+"/"+elem.getKey()+"/"+sens.getKey();
					//String v = String.format("%.2f", sens.getValue().getAsFloat());	
					
					String observation = topic2observation.get(t);
					
					if (observation == null) {
						logger.warn(mapperUri+ " MAPPING NOT FOUND FOR TOPIC: "+t);
						continue;
					}
					
					mapping.add(new String[] {observation,sens.getValue().getAsString()});
				}
			}
			/*
			 * "measure1" : value1
			 * */
			else {
				String t = topic+"/"+elem.getKey();
				//String v = String.format("%d", elem.getValue().getAsInt());
				//String v = String.format("%.2f", elem.getValue().getAsFloat());
				
				String observation = topic2observation.get(t);
				
				if (observation == null) {
					logger.warn(mapperUri+ " MAPPING NOT FOUND FOR TOPIC: "+t);
					continue;
				}
				
				mapping.add(new String[] {observation,elem.getValue().getAsString()});
			}
		}
		
		return mapping;
	}

}
