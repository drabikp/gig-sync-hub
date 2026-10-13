package sk.drabikp.gigsynchub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import sk.drabikp.gigsynchub.gig.application.PublicAddresses;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GigSyncHubApplication {

	static {
		PublicAddresses.holdResolvedNamesStill();
	}

	public static void main(String[] args) {
		SpringApplication.run(GigSyncHubApplication.class, args);
	}

}
