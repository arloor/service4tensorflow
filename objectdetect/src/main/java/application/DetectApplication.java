package application;

import application.model.DetectResult;
import application.rabbitmq.RabbitmqHelper;
import application.service.DetectService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.List;


@RestController
@SpringBootApplication
@EnableEurekaClient
public class DetectApplication {

    @Value("${server.port}")
    int port;

    private static Logger logger = LoggerFactory.getLogger(DetectApplication.class);

    public static void main(String[] args) throws JsonProcessingException {
        ApplicationContext app = SpringApplication.run(DetectApplication.class, args);
        //初始化DetectService的模型和标签
        app.getBean(DetectService.class).reload();

        ArrayList<String> urls = new ArrayList<>();
        urls.add("http://localhost:8000/1.jpg");
        urls.add("http://localhost:8000/2.jpg");
        ObjectMapper mapper = new ObjectMapper();
        logger.info("请求体例子: " + mapper.writeValueAsString(urls));
    }




}
