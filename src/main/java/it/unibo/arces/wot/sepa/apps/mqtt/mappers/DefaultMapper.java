package it.unibo.arces.wot.sepa.apps.mqtt.mappers;

import java.io.IOException;
import java.util.ArrayList;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.security.ClientSecurityManager;

public class DefaultMapper extends MqttMapper {
	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException, IOException{			
		DefaultMapper mapper = new DefaultMapper(null);
		
		mapper.start();
		
		synchronized(mapper) {
			mapper.wait();
		}	
	}
	
	public DefaultMapper(ClientSecurityManager sm)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException {
		super(sm,"mqtt:DefaultMapper");
	}
	
	public DefaultMapper()
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException {
		super("mqtt:DefaultMapper");
	}

	@Override
	protected ArrayList<String[]> map(String topic, String value) {
		ArrayList<String[]> ret = new ArrayList<String[]>();
	
		String observation = topic2observation.get(topic);
		
		if (observation != null) {
			ret.add(new String[] { observation, value });
			logger.debug(mapperUri+" Topic: "+topic+" Value: "+value+" ==> Observation: "+observation+ " Value: "+value);
		}
		else {
			logger.warn(mapperUri+ " MAPPING NOT FOUND FOR TOPIC: "+topic);
		}
		
		return ret;
	}
}
