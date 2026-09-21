package likelion.kitalk.global.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringJUnitConfig(SecurityConfigTest.TestConfig.class)
@WebAppConfiguration
class SecurityConfigTest {
  @Configuration
  @EnableWebSecurity
  @EnableWebMvc
  @Import({SecurityConfig.class, TestController.class})
  static class TestConfig {}

  @RestController
  static class TestController {
    @GetMapping("/api/menu/example")
    String menu() { return "menu"; }

    @GetMapping("/api/docs")
    String docs() { return "docs"; }

    @GetMapping("/api/swagger-ui/index.html")
    String swaggerAsset() { return "swagger"; }

    @GetMapping("/private/example")
    String privateEndpoint() { return "private"; }
  }

  @Autowired private WebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void kioskMenuIsPublic() throws Exception {
    mvc.perform(get("/api/menu/example")).andExpect(status().isOk());
  }

  @Test
  void docsArePublic() throws Exception {
    mvc.perform(get("/api/docs")).andExpect(status().isOk());
    mvc.perform(get("/api/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  void unknownEndpointIsDenied() throws Exception {
    mvc.perform(get("/private/example")).andExpect(status().isForbidden());
  }
}
