package it.unibo.arces.wot.sepa.apps.mqtt;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.security.ClientSecurityManager;
import it.unibo.arces.wot.sepa.commons.sparql.Bindings;
import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermLiteral;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermURI;
import it.unibo.arces.wot.sepa.pattern.Aggregator;
import it.unibo.arces.wot.sepa.pattern.JSAP;

public class ObservationLogger extends Aggregator {
	private static final Logger logger = LogManager.getLogger();
	
	public static void main(String[] args) throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, SEPABindingsException, InterruptedException, IOException {
		ObservationLogger logger = new ObservationLogger();
		
		logger.subscribe(5000);
		
		synchronized(logger) {
			logger.wait();
		}
		
		logger.close();
	}
	
	public ObservationLogger(ClientSecurityManager sm)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException {
		super(new JSAP("mqtt.jsap"), "OBSERVATIONS", "LOG_QUANTITY",sm);
	}
	
	public ObservationLogger()
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException {
		super(new JSAP("mqtt.jsap"), "OBSERVATIONS", "LOG_QUANTITY",null);
	}

	@Override
	public void onAddedResults(BindingsResults results) {
		for (Bindings binding : results.getBindings()) {
			if (binding.getValue("value").equals("NaN")) continue;

//			logger.info("Logging: "+binding.getValue("observation") +" "+binding.getValue("value"));
			
			try {
				this.setUpdateBindingValue("observation", new RDFTermURI(binding.getValue("observation")));
				this.setUpdateBindingValue("value", new RDFTermLiteral(binding.getValue("value"), binding.getDatatype("value")));
				this.setUpdateBindingValue("timestamp", new RDFTermLiteral(binding.getValue("timestamp"), binding.getDatatype("timestamp")));
				
				int nRetry = 5;
				Response ret;
				do{
					logger.info("Logging: "+binding.getValue("observation") +" "+binding.getValue("value")+" retry: "+nRetry);
					ret = update();
					if (ret.isError()) logger.error(ret);
					nRetry = nRetry-1;
				}
				while(ret.isError() && nRetry > 0);
			} catch (SEPASecurityException | SEPAProtocolException | SEPAPropertiesException | SEPABindingsException e) {
				e.printStackTrace();
			}
		}
	}
}
