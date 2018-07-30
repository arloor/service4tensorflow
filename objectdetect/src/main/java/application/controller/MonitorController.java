package application.controller;

import application.model.Status;
import application.rabbitmq.RabbitmqHelper;
import application.service.DetectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/monitor")
public class MonitorController {

    @Autowired
    RabbitmqHelper rabbitmqHelper;
    @Autowired
    DetectService detectService;

    @RequestMapping("/status")
    public Status send(){
        Status status=detectService.staus(rabbitmqHelper.staus(new Status()));
        return status;
    }
}
