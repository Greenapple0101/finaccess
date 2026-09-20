package io.github.greenapple0101.finaccess.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public HelloResponse hello() {
        return new HelloResponse("Hello, FinAccess!");
    }

    public record HelloResponse(String message) {
    }
}
