package application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;


@RestController
@SpringBootApplication
//@EnableEurekaClient
public class DetectApplication {

    @Value("${server.port}")
    int port;

    private static Logger logger=LoggerFactory.getLogger(DetectApplication.class);

    @Autowired
    DetectService detectService;

    public static void main(String[] args) throws JsonProcessingException {
        ArrayList<String> urls=new ArrayList<>();
        urls.add("http://jianbujingimages.moontell.cn/FrrkTtsITfXki44oJqk6i3IUzv2x");
        urls.add("http://jianbujingimages.moontell.cn/FhD-asgS-HOuUssL1dVzmgkhD2v-");
        ObjectMapper mapper=new ObjectMapper();

        SpringApplication.run(DetectApplication.class,args);
        logger.info("请求体例子: "+mapper.writeValueAsString(urls));;
    }

    @RequestMapping("/service")
    public List<DetectResult> service(@RequestBody List<String> ImageURLs) throws Exception {
        return detectService.doService(ImageURLs);
    }

    @RequestMapping("/hi")
    public String hi(){
        return "greeting from "+ port+"\r\n";
    }

}
