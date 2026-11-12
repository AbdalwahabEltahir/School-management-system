package qa.sijill.sijill.shared.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The platform name, stored once ({@code sijill.brand} in application.yml). Emails, PDF reports and the web interface
 * all read it from here, so renaming the platform is a one-line change.
 */
@Component
public class Brand {

    private static volatile String en = "Hafez";
    private static volatile String ar = "حافظ";

    private final String configuredEn;
    private final String configuredAr;

    Brand(@Value("${sijill.brand.name-en:Hafez}") String en, @Value("${sijill.brand.name-ar:حافظ}") String ar) {
        this.configuredEn = en;
        this.configuredAr = ar;
    }

    @PostConstruct
    void publish() {
        en = configuredEn;
        ar = configuredAr;
    }

    public static String en() { return en; }

    public static String ar() { return ar; }
}
