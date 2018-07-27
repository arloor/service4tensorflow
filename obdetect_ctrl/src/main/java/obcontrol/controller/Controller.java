package obcontrol.controller;


import obcontrol.RabbitmqHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class Controller {
    @Autowired
    RabbitmqHelper rabbitmqHelper;

    @RequestMapping("/ctrl_info")
    public String sendCtrl(@RequestParam String content){
        rabbitmqHelper.send(content);
        return "控制消息 "+content+" 发送成功";
    }
}
