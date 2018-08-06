package obcontrol.controller;

import obcontrol.rabbitmq.RabbitmqHelper;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tensorflow.SavedModelBundle;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/model")
public class ModelController {
    @Value("${filepath.relative.testModelDir}")
    private String testModelDir;

    private static Logger logger=LoggerFactory.getLogger(ModelController.class);

    private String modelURL="http://localhost:9000/saved_model.pb";

    @Autowired
    RabbitmqHelper rabbitmqHelper;

    @RequestMapping("/updateSingleModel")
    public String updateSingleModel(@RequestParam String nodeName,@RequestParam String modelURL,org.apache.catalina.servlet4preview.http.HttpServletRequest request){
        String result=testModelUrl(modelURL,request);
        if(result.equals("valid")){
            this.setModelURL(modelURL);
            //通知所有节点更新模型5
            rabbitmqHelper.send(nodeName+"::update::"+modelURL);
            return "已向"+nodeName+"发送更新模型命令,更新后的模型地址将会是 "+modelURL;
        }else{
            return result;
        }
    }

    @RequestMapping("/updateAllModel")
    public  String updateAllModel(@RequestParam String modelURL,
                                 org.apache.catalina.servlet4preview.http.HttpServletRequest request){
        String result=testModelUrl(modelURL,request);
        if(result.equals("valid")){
            this.setModelURL(modelURL);
            //通知所有节点更新模型5
            rabbitmqHelper.send("all::update::"+modelURL);
            return "已向所有节点发送更新模型命令";
        }else{
            return result;
        }


    }

    //在这里加锁，保证一次只有一个在下载和写文件，要是同时写文件，有文件锁
    private synchronized String testModelUrl(String modelURL, HttpServletRequest request) {
        logger.info("开始检测url及模型的有效性");
        String targetPath=testModelDir +"/saved_model.pb";
        //测试输入的url文件是否有效,是否能够下载
        try(InputStream is=new URL(modelURL).openStream()) {
            URL url=new URL(modelURL);

            Path target = Paths.get(targetPath);
            Files.createDirectories(target.getParent());
//            System.out.println(targetPath);

            Files.copy(is, target,StandardCopyOption.REPLACE_EXISTING);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return "模型地址URL不正确 "+e.getMessage();
        } catch (IOException e) {
            e.printStackTrace();
            return "模型地址URL无效 "+e.getMessage();
        }catch (IllegalArgumentException e){
            e.printStackTrace();
            return "模型地址URL无效 "+e.getMessage();
        }catch (Exception e){
            return "模型地址URL无效 "+e.getMessage();
        }

        //测试模型是否能有效加载
        try(SavedModelBundle model=SavedModelBundle.load(testModelDir, "serve")){
            return "valid";

        }catch (org.tensorflow.TensorFlowException e){
            return "模型文件无效，请输入tag为serve的有效模型地址";
        }
    }

    @RequestMapping("/modelURL")
    public String defaultModelURL(){
        return modelURL;
    }

    public String getModelURL() {
        return modelURL;
    }

    public void setModelURL(String modelURL) {
        this.modelURL = modelURL;
    }
}
