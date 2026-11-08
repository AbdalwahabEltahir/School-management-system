package qa.sijill.sijill;

import org.springframework.boot.SpringApplication;

public class TestSijillApplication {

	public static void main(String[] args) {
		SpringApplication.from(SijillApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
