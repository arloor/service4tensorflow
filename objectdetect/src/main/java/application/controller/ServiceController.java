package application.controller;

import application.model.DetectResult;
import application.service.DetectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ServiceController {

    @Autowired
    DetectService detectService;

    @RequestMapping("/service")
    public List<DetectResult> service(@RequestBody List<String> ImageURLs) throws Exception {
        return detectService.doService(ImageURLs);
    }
}
