package qa.hafez.hafez;

import org.springframework.boot.SpringApplication;

public class TestHafezApplication {

	public static void main(String[] args) {
		SpringApplication.from(HafezApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
