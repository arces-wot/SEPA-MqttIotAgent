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

/**

TOPIC: swamp/devices/moisture1/up
VALUE: {"app_id":"swamp","dev_id":"moisture1","hardware_serial":"0421558EF1FB7101","port":2,"counter":4058,"payload_raw":"A74M6lk=","payload_fields":{"Battery_Level":89,"Moisture_Signal_to_Supply_Perc":28.98,"Moisture_Supply_Voltage":3.306},"metadata":{"time":"2019-11-22T17:10:54.766350109Z","frequency":868.3,"modulation":"LORA","data_rate":"SF12BW125","airtime":1318912000,"coding_rate":"4/5","gateways":[{"gtw_id":"eui-c0ee40ffff296623","timestamp":3086341940,"time":"","channel":1,"rssi":-84,"snr":9.5,"rf_chain":1,"latitude":44.78009,"longitude":10.717014,"altitude":4,"location_source":"registry"}]}}

TOPIC: transmission_example/devices/new_device/up
VALUE: {"app_id":"transmission_example","dev_id":"new_device","hardware_serial":"00A76EC32F31EC32","port":2,"counter":624,"payload_raw":"IAJ3ABQOCQoBAgAEdwAWUQk8AWQABncAEyUJAAE+ADQ=","payload_fields":{"battery":69,"sensor_2":{"Conductivity":244,"Permittivity":18.52,"Temperature":11.3,"WaterContent":0.413},"sensor_4":{"Conductivity":336,"Permittivity":21.54,"Temperature":11.2,"WaterContent":0.413},"sensor_6":{"Conductivity":307,"Permittivity":18.47,"Temperature":11.3,"WaterContent":0.413}}},"metadata":{"time":"2019-12-02T14:54:58.581619617Z","frequency":868.5,"modulation":"LORA","data_rate":"SF12BW125","airtime":2138112000,"coding_rate":"4/5","gateways":[{"gtw_id":"eui-0ceee6fffe9da82e","timestamp":2000871028,"time":"2019-12-02T14:54:56.822372Z","channel":2,"rssi":-113,"snr":-8,"rf_chain":1,"latitude":44.48149,"longitude":11.33015,"altitude":231}]}}
 
 * 
 * */
public class MeterMapper extends MqttMapper {

	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException, IOException{			
		MeterMapper mapper = new MeterMapper();
		
		mapper.start();
		
		synchronized(mapper) {
			mapper.wait();
		}
	}
	
	public MeterMapper()
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException {
		super("mqtt:MeterMapper");
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
		
		if (!json.has("payload_fields")) {
			logger.error(mapperUri+" payload_fields member not found: "+value);
			return mapping;
		}
		
		json = json.getAsJsonObject("payload_fields");
		
		for (Entry<String, JsonElement> elem : json.entrySet()) {
		
			/*
			 * "sensor****": {"measure1" : value1, "measure2" : value2}
			 * */
			if (elem.getKey().startsWith("sensor")) {
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
