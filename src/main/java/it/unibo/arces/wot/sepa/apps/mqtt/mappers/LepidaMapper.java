package it.unibo.arces.wot.sepa.apps.mqtt.mappers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.security.ClientSecurityManager;

public class LepidaMapper extends MqttMapper {
	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException, IOException{			
		LepidaMapper mapper = new LepidaMapper();
		
		mapper.map("topic", "{\"data\":\"A7wM41Q=\"}");
		mapper.start();
		
		synchronized(mapper) {
			mapper.wait();
		}
	}
	
	/**
	 * {"applicationID":"1","applicationName":"LoraWanDev","deviceName":"SICE-ATRS0006-6",
	 * "devEUI":"0171fbf18e552104","rxInfo":[{"gatewayID":"00800000a00047d1",
	 * "name":"Novellara01","time":"2020-06-22T14:03:19.981679Z","rssi":-115,"loRaSNR":-18.8,
	 * "location":{"latitude":44.84463,"longitude":10.73136,"altitude":58}}],
	 * "txInfo":{"frequency":867700000,"dr":0},"adr":false,"fCnt":9167,"fPort":2,
	 * 
	 * "data":"A7wM41Q="
	 * 
	 * }
	 * 
	 * */
	public LepidaMapper(ClientSecurityManager sm, String uri)
			throws SEPAProtocolException, SEPAPropertiesException, SEPASecurityException {
		super(sm, "mqtt:LepidaMapper");
	}
	
	public LepidaMapper()
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException {
		super("mqtt:LepidaMapper");
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
			return mapping;
		}
		
		/*
		 * Ecco un esempio del payload (5 bytes) e relativo json:
		 
		0x03 0x51 0x0C 0xEC 0x55
		 
		{
		  "Battery_Level": 85,
		  "Moisture_Signal_to_Supply_Perc": 25.67,
		  "Moisture_Supply_Voltage": 3.308
		}
		 * **/
		// Payload
		byte[] decoded = Base64.getDecoder().decode(json.get("data").getAsString());
		
		//Concatenazione del byte 3 e 4 del payload (valore in mV della tensione di alimentazione del sensore)
		int MoistureVoltage_MSB = (((int) decoded[2]) << 8) & 0xF00;
		int MoistureVoltage_LSB = ((int) decoded[3]) & 0xFF;
		float MoistureVoltage_int = MoistureVoltage_MSB | MoistureVoltage_LSB;
		float MoistureVoltage = MoistureVoltage_int/1000;
		
		//Calcolo percentuale del valore del sensore in rapporto alla sua tensione di alimentazione
		int MoistureSignal_MSB = (((int) decoded[0]) << 8) & 0xF00;
		int MoistureSignal_LSB = ((int) decoded[1]) & 0xFF;
		float MoistureSignal_int = MoistureSignal_MSB | MoistureSignal_LSB;
		float MoistureSignalRatio =  (MoistureSignal_int / MoistureVoltage_int) * 100;
		
		//Byte 5 del payload (valore della percentuale della batteria)
		int Bat = decoded[4];  
		
		mapping.add(new String[] {topic2observation.get(topic+"/Moisture_Signal_to_Supply_Perc"),String.format("%.2f", MoistureSignalRatio)});
		mapping.add(new String[] {topic2observation.get(topic+"/Moisture_Supply_Voltage"),String.format("%.3f", MoistureVoltage)});
		mapping.add(new String[] {topic2observation.get(topic+"/Battery_Level"),String.format("%d", Bat)});
		 
		return mapping;
	}

}
