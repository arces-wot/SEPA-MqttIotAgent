package it.unibo.arces.wot.sepa.apps.mqtt;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;

public class SSL {
	private static final Logger logger = LogManager.getLogger();
	
	static TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
		public java.security.cert.X509Certificate[] getAcceptedIssuers() {
			logger.debug("getAcceptedIssuers");
			return new X509Certificate[0];
		}

		public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			logger.debug("checkClientTrusted");
		}

		public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			logger.debug("checkServerTrusted");
		}
	} };
	
	public static SSLContext getSSLContextTrustAllCa(String protocol) throws SEPASecurityException {
		SSLContext sc = null;
		try {
			sc = SSLContext.getInstance(protocol);
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			throw new SEPASecurityException(e);
		}

		return sc;
	}
	
	public static SSLContext getSSLContextFromCertFile(String protocol, String caCertFile) throws SEPASecurityException {
		try {
			// Load certificates from caCertFile into the keystore
			KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
			caKs.load(null, null);

			FileInputStream fis = new FileInputStream(caCertFile);
			BufferedInputStream bis = new BufferedInputStream(fis);
			CertificateFactory cf;
			cf = CertificateFactory.getInstance("X.509");
			while (bis.available() > 0) {
				X509Certificate caCert = (X509Certificate) cf.generateCertificate(bis);
				caKs.setCertificateEntry(caCert.getIssuerX500Principal().getName(), caCert);
			}

			// Trust manager
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
			tmf.init(caKs);

			// Create SSL context
			SSLContext sslContext = SSLContext.getInstance(protocol);
			sslContext.init(null, tmf.getTrustManagers(), null);

			return sslContext;
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException
				| KeyManagementException e) {
			e.printStackTrace();
			throw new SEPASecurityException(e);
		}
	}
}
