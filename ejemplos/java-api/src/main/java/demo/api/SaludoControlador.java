package demo.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaludoControlador {

    @GetMapping("/saludo")
    public Map<String, String> saludo(@RequestParam(defaultValue = "mundo") String nombre) {
        return Map.of("mensaje", "Hola, " + nombre);
    }
}
