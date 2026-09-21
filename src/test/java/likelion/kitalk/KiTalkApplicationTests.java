package likelion.kitalk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "KITALK_INTEGRATION_TESTS", matches = "true")
class KiTalkApplicationTests {

  @Test
  void contextLoads() {
  }

}
