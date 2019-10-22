package it.unibo.arces.wot.sepa.apps.mqtt;

import java.io.IOException;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.security.SEPASecurityManager;
import it.unibo.arces.wot.sepa.pattern.JSAP;

public class MqttBrokerTesting {
	
	public static void main(String[] args) throws SEPASecurityException, InterruptedException, SEPAProtocolException, SEPAPropertiesException, SEPABindingsException, IOException {
		// Wizzilab
//		String url ="roger.wizzilab.com";
//		int port = 8883;
//		String clientId = "ffa574972ab9:1";
//		String caCertFile = "/etc/ssl/cert.pem";
//		String user = "ffa574972ab9";
//		String password = "6e257b56172ea934d79ee1f5c2c1c7a9";
//		String protocol = "SSL";

		// TTN - Meter soil moisture
		//Topic: transmission_example/devices/new_device/up
		String url = "eu.thethings.network";
		String user = "transmission_example";
		String password = "ttn-account-v2.XHaR_oEYSO4MjbsLRh_63bf8Y7IwTp4dBCzlkWA-NM4";
		int port = 1883;		
		String clientId = null;
		String caCertFile = null;
		String protocol = null;
		
		// MML
//		String url ="giove.arces.unibo.it";
//		int port = 52877;
//		String clientId = null;
//		String caCertFile = null;
//		String user = null;
//		String password = null;
//		String protocol = null;
		
		MqttAdapter clientAdapter = new MqttAdapter(new JSAP("mqtt.jsap"), new SEPASecurityManager("sepa.jks", "sepa2017", "sepa2017", null), url, port, clientId, user, password, protocol, caCertFile);
		
		synchronized (clientAdapter) {
			clientAdapter.wait();	
		}
		clientAdapter.close();
	}
}
