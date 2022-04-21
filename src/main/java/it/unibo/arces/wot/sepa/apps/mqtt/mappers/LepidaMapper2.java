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

public class LepidaMapper2 extends MqttMapper {

	/*
	 * {"applicationID":"1","applicationName":"LoraWanDev","deviceName":
	 * "UniBO-Multisensor-9", "devEUI":"b913583fd90cdd00",
	 * "rxInfo":[{"gatewayID":"00800000a00047d1","name":"Novellara01","time":
	 * "2021-02-01T14:19:03.189779Z","rssi":-115,"loRaSNR":-18.8,
	 * "location":{"latitude":44.84471,"longitude":10.73116,"altitude":27}}],
	 * "txInfo":{"frequency":868500000,"dr":0},"adr":false,"fCnt":408,"fPort":2,
	 * "data":"DQF3ABMZBCgC+ABkPA=="}
	 * 
	 * 
	 * Payload (base64): DQF3ABFXAloCIABkPA== Payload (hex):
	 * 0d0177001157025a022000643c
	 * 
	 * { "Battery": 100, "Dutycycle_min": 60, "sensore_1": { "Conductivity": 544,
	 * "Permittivity": 17.87, "Temperature": 2.9, "WaterContent": 0.382 } } 
	 * 
	 * TEROS: Product Number: 0x67 
	 * GS3: Product Number: 0x77 
	 * HYDROS: Product Number: 0x69
	 * 
	 */

	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException,
			SEPABindingsException, InterruptedException, IOException {
		LepidaMapper2 mapper = new LepidaMapper2();

		// Bertacchini 4 sensori
		// 2c01670b9a46042803090002670b7c46045003c20003670b0832061402720006690f6f000d3c050a0000643c
		//mapper.map("topic", "{\"data\":\""+Base64.getEncoder().encodeToString(new byte[] {0x2c,0x01,0x67,0x0b,(byte)0x9A,0x46,0x04,0x28,0x03,0x09,0x00,0x02,0x67,0x0b,0x7c,0x46,0x04,0x50,0x03,(byte)0xC2,0x00,0x03,0x67,0x0b,0x08,0x32,0x06,0x14,0x02,0x72,0x00,0x06,0x69,0x0f,0x6f,0x00,0x0d,0x3c,0x05,0x0a,0x00,0x00,0x64,0x3c})+"\"}");
		
		// ARCES 2 sensori
		// 170177000311071e00010002770010400714008d000014
		//mapper.map("topic", "{\"data\":\""+Base64.getEncoder().encodeToString(new byte[] {0x17,0x01,0x77,0x00,0x03,0x11,0x07,0x1e,0x00,0x01,0x00,0x02,0x77,0x00,0x10,0x40,0x07,0x14,0x00,(byte)0x8d,0x00,0x00,0x14})+"\"}");
		
		
		//mapper.map("topic", "{\"data\":\"DQF3ABFXAloCIABkPA==\"}");
		mapper.start();

		synchronized (mapper) {
			mapper.wait();
		}
	}

	public LepidaMapper2() throws SEPAProtocolException, SEPAPropertiesException, SEPASecurityException {
		super("mqtt:LepidaMapper2");
	}

	// WC_MSB | WC_LSB | WC_DEC (/100) | TEMP | TEMP_DEC (/100) | COND_MSB | COND_LSB | COND_DEC (/100)
	private static final byte TEROS = 0x67;
	private static final int TEROS_LEN = 8;

	private ArrayList<String[]> parseTeros(String topic,int port, byte[] data) {
		ArrayList<String[]> mapping = new ArrayList<String[]>();

		int vwc_MSB = (((int) data[0]) << 8) & 0xFF00;
		int vwc_LSB = ((int) data[1]) & 0xFF;
		float vwc = vwc_MSB | vwc_LSB;
		vwc = vwc + (float) data[2] / 100;
		
		float wc = (float)(0.0000000006771*Math.pow(vwc,3) - 0.000005105*Math.pow(vwc,2) + 0.01302*vwc - 10.848);
		
		String obs = topic2observation.get(topic + "/sensor_"+port+"/WaterContent");
		if (obs != null) mapping.add(new String[] {obs,
				String.format("%.2f", wc) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/WaterContent");
		
		float temp = (float) data[3] + (float) data[4] / 100;
		
		obs = topic2observation.get(topic + "/sensor_"+port+"/Temperature");
		if (obs != null) mapping.add(new String[] { obs,
				String.format("%.2f", temp) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/Temperature");
		
		int cond_MSB = (((int) data[5]) << 8) & 0xFF00;
		int cond_LSB = ((int) data[6]) & 0xFF;
		float cond = cond_MSB | cond_LSB;
		cond = cond + (float) data[7] / 100;
		
		obs = topic2observation.get(topic + "/sensor_"+port+"/Conductivity");
		if (obs != null) mapping.add(new String[] { topic2observation.get(topic + "/sensor_"+port+"/Conductivity"),
				String.format("%.2f", cond) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/Conductivity");
		
		return mapping;
	}

	// PERM_MSB | PERM_LSB | PERM_DEC (/100) | TEMP | TEMP_DEC (/100) | COND_MSB | COND_LSB | COND_DEC (/100)
	private static final byte GS3 = 0x77;
	private static final int GS3_LEN = 8;

	private ArrayList<String[]> parseGS3(String topic,int port, byte[] data) {
		ArrayList<String[]> mapping = new ArrayList<String[]>();

		int perm_MSB = (((int) data[0]) << 8) & 0xFF00;
		int perm_LSB = ((int) data[1]) & 0xFF;
		float perm = perm_MSB | perm_LSB;
		perm = perm + (float) data[2] / 100;
		
		String obs = topic2observation.get(topic + "/sensor_"+port+"/Permittivity");
		if (obs != null)mapping.add(new String[] { obs,
				String.format("%.2f", perm) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/Permittivity");
		
		float temp = (float) data[3] + (float) data[4] / 100;
		
		obs = topic2observation.get(topic + "/sensor_"+port+"/Temperature");
		if (obs != null) mapping.add(new String[] { obs,
				String.format("%.2f", temp) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/Temperature");
		
		int cond_MSB = (((int) data[5]) << 8) & 0xFF00;
		int cond_LSB = ((int) data[6]) & 0xFF;
		float cond = cond_MSB | cond_LSB;
		cond = cond + (float) data[7] / 100;
		
		obs = topic2observation.get(topic + "/sensor_"+port+"/Conductivity");
		if (obs != null) mapping.add(new String[] { obs,
				String.format("%.2f", cond) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/Conductivity");
		
		float wc = (float) (0.118*Math.sqrt(perm)-0.117);
		obs = topic2observation.get(topic + "/sensor_"+port+"/WaterContent");
		if (obs != null) mapping.add(new String[] { obs,
				String.format("%.3f", wc) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/WaterContent");
		
		return mapping;
	}

	// 0x2D | WD (negative) | WD_DEC (/100) | TEMP | TEMP_DEC (/100) | COND_MSB | COND_LSB | COND_DEC (/100) | FREEZING
	// WD_MSB | WD_LSB | WD_DEC (/100) | TEMP | TEMP_DEC (/100) | COND_MSB | COND_LSB | COND_DEC (/100) | FREEZING
	private static final byte HYDROS = 0x69;
	private static final int HYDROS_LEN = 9;

	private ArrayList<String[]> parseHydros(String topic,int port, byte[] data) {
		ArrayList<String[]> mapping = new ArrayList<String[]>();

		String obs = null;
		
		if (data[0] == 0x2D) {
			float wd = -((float) data[1] + (float) data[2]/100);;
			
			obs = topic2observation.get(topic + "/sensor_"+port+"/WaterDepth");
			if (obs != null) mapping.add(new String[] { obs,
					String.format("%.2f", wd) });
			else
				logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/WaterDepth");
		}
		else {
			int wd_MSB = (((int) data[0]) << 8) & 0xFF00;
			int wd_LSB = ((int) data[1]) & 0xFF;
			float wd = wd_MSB | wd_LSB;
			wd = wd + (float) data[2] / 100;
			obs = topic2observation.get(topic + "/sensor_"+port+"/WaterDepth");
			if (obs != null) mapping.add(new String[] { obs,
					String.format("%.2f", wd) });
			else
				logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/WaterDepth");
		}
		
		float temp = (float) data[3] + (float) data[4] / 100;
		
		obs = topic2observation.get(topic + "/sensor_"+port+"/Temperature");
		if (obs != null) mapping.add(new String[] { obs,
				String.format("%.2f", temp) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/Temperature");
		
		int cond_MSB = (((int) data[5]) << 8) & 0xFF00;
		int cond_LSB = ((int) data[6]) & 0xFF;
		float cond = cond_MSB | cond_LSB;
		cond = cond + (float) data[7] / 100;
		
		obs = topic2observation.get(topic + "/sensor_"+port+"/Conductivity");
		if (obs != null) mapping.add(new String[] { obs,
				String.format("%.2f", cond) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/Conductivity");
		
		obs = topic2observation.get(topic + "/sensor_"+port+"/FreezingFlag");
		if (obs != null) mapping.add(new String[] { obs,
				String.format("%d", data[8]) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/sensor_"+port+"/FreezingFlag");
		
		return mapping;
	}

	@Override
	protected ArrayList<String[]> map(String topic, String value) {
		ArrayList<String[]> mapping = new ArrayList<String[]>();

		JsonObject json = null;
		try {
			json = JsonParser.parseString(value).getAsJsonObject();
		} catch (JsonParseException e) {
			logger.error(mapperUri + " " + e.getMessage());
			return mapping;
		}

		// Payload
		// [numero di byte]
		// [numero porta][product number][dati]
		// [numero porte][product number][dati]
		// ..
		// [numero porta][product number][dati]
		// [livello batteria][dutycycle]
		byte[] decoded = Base64.getDecoder().decode(json.get("data").getAsString());
		
		// Battery and duty cycle only (no connected sensors) ==> 3 bytes
		int index = 1;
		if (decoded.length != 3) {
			do {
				byte[] data;
				switch(decoded[index+1]) {
					case TEROS: 
						data = new byte[TEROS_LEN];
						for (int i=0; i < TEROS_LEN; i++) {
							data[i] = decoded[index+2+i];
						}
						mapping.addAll(parseTeros(topic,decoded[index], data));
						index = index + 2 + TEROS_LEN;
						break;
					case GS3: 
						data = new byte[GS3_LEN];
						for (int i=0; i < GS3_LEN; i++) {
							data[i] = decoded[index+2+i];
						}
						mapping.addAll(parseGS3(topic,decoded[index], data));
						index = index + 2 + GS3_LEN;
						break;
					case HYDROS: 
						data = new byte[HYDROS_LEN];
						for (int i=0; i < HYDROS_LEN; i++) {
							data[i] = decoded[index+2+i];
						}
						mapping.addAll(parseHydros(topic,decoded[index], data));
						index = index + 2 + HYDROS_LEN;
						break;
				}
				
			} while (index < decoded.length - 3);
		}

		String obs = topic2observation.get(topic + "/Battery");
		if (obs != null) mapping.add(new String[] { obs,
				String.format("%d", decoded[decoded.length - 2]) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/Battery");
			
		obs = topic2observation.get(topic + "/Dutycycle_min");
		if (obs != null) mapping.add(new String[] { topic2observation.get(topic + "/Dutycycle_min"),
				String.format("%d", decoded[decoded.length - 1]) });
		else
			logger.warn("MAPPING NOT FOUND: "+topic + "/Dutycycle_min");
		
		logger.info(mapperUri+" Observations: "+mapperUri);
		
		return mapping;
	}

}
