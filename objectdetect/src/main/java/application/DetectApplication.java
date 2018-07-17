package application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;


@RestController
@SpringBootApplication
public class DetectApplication {

    private static Logger logger=LoggerFactory.getLogger(DetectApplication.class);

    @Autowired
    DetectService detectService;

    public static void main(String[] args) throws JsonProcessingException {
        ArrayList<String> urls=new ArrayList<>();
        urls.add("images/test.jpg");
        urls.add("images/test1.jpg");
        ObjectMapper mapper=new ObjectMapper();

        SpringApplication.run(DetectApplication.class,args);
        logger.info("请求体例子: "+mapper.writeValueAsString(urls));;
    }

    @RequestMapping("/service")
    public List<DetectResult> service(@RequestBody List<String> ImageURLs) throws Exception {
        return detectService.doService(ImageURLs);
    }

}
