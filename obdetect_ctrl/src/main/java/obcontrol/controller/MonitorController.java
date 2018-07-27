package obcontrol.controller;


import obcontrol.model.Status;
import obcontrol.rabbitmq.RabbitmqHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/")
public class MonitorController {
    @Autowired
    RabbitmqHelper rabbitmqHelper;

    @RequestMapping("/ctrl_info")
    public String sendCtrl(@RequestParam String content){
        rabbitmqHelper.send(content);
        return "控制消息 "+content+" 发送成功";
    }

    @RequestMapping("/for_all_status")
    public String askForAllStatus(){
        rabbitmqHelper.send("all::status");
        return "已发送请求所有节点状态的异步消息，请稍后点击查看所有节点状态";
    }

    @RequestMapping("/for_single_status")
    public String askForSingleStatus(@RequestParam String nodeName){
        rabbitmqHelper.send(nodeName+"::status");
        return "已发送请求节点"+nodeName+"状态的异步消息，请稍后点击查看节点状态";
    }

    @RequestMapping("/get_all_status")
    public Map<String,Status> getAllStatus(){
        Map<String,Status> nodeStatusMap=rabbitmqHelper.getNodeStatusMap();
        return nodeStatusMap;
    }
}
