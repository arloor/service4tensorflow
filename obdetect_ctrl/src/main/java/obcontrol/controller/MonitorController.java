package obcontrol.controller;


import obcontrol.model.Status;
import obcontrol.rabbitmq.RabbitmqHelper;
import obcontrol.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/")
public class MonitorController {
    @Autowired
    RabbitmqHelper rabbitmqHelper;

    @RequestMapping("/delete_single_status")
    public Collection<Status> delete_single_status(@RequestParam String nodeName){
        Map<String,Status> nodeStatusMap=rabbitmqHelper.deleteNodeInfo(nodeName);
        return nodeStatusMap.values();
    }

    @RequestMapping("/for_all_status")
    public String askForAllStatus(){
        rabbitmqHelper.send("all::status");
        return "已发送请求所有节点状态的异步消息，请稍后点击“显示最新状态”或刷新页面";
    }

    @RequestMapping("/for_single_status")
    public String askForSingleStatus(@RequestParam String nodeName){
        rabbitmqHelper.send(nodeName+"::status");
        return "已发送请求节点"+nodeName+"状态的异步消息，请稍后点击“显示最新状态”或刷新页面";
    }

    @RequestMapping("/get_all_status")
    public Collection<Status> getAllStatus(){
        Map<String,Status> nodeStatusMap=rabbitmqHelper.getNodeStatusMap();

        for (Map.Entry<String,Status> entry:nodeStatusMap.entrySet()
             ) {
            Status status=entry.getValue();
            Date date= Utils.formDate(status.getLastMsgTime());
            Calendar now=Calendar.getInstance();
            now.add(Calendar.MINUTE,-5);
            if(date.before(now.getTime())){
                status.setExpired(true);
            }
//            status.setExpired(true);
        }
        return nodeStatusMap.values();
    }
}
