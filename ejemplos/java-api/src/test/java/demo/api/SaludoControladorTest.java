package demo.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SaludoControlador.class)
class SaludoControladorTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void saludaPorDefecto() throws Exception {
        mvc.perform(get("/saludo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mensaje").value("Hola, mundo"));
    }

    @Test
    void saludaPorNombre() throws Exception {
        mvc.perform(get("/saludo").param("nombre", "dev"))
            .andExpect(jsonPath("$.mensaje").value("Hola, dev"));
    }
}
