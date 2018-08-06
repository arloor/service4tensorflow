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
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.RandomAccess;

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
    public String updateSingleModel(@RequestParam String nodeName,@RequestParam String modelURL){
        String result=testModelUrl(modelURL);
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
        String result=testModelUrl(modelURL);
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
    private synchronized String testModelUrl(String modelURL) {
//        //无断点续传功能版本
//        logger.info("开始检测url及模型的有效性");
//        String targetPath=testModelDir +"/saved_model.pb";
//        //测试输入的url文件是否有效,是否能够下载
//        try(InputStream is=new URL(modelURL).openStream()) {
//            URL url=new URL(modelURL);
//
//            Path target = Paths.get(targetPath);
//            Files.createDirectories(target.getParent());
////            System.out.println(targetPath);
//
//            Files.copy(is, target,StandardCopyOption.REPLACE_EXISTING);
//        } catch (MalformedURLException e) {
//            e.printStackTrace();
//            return "模型地址URL不正确 "+e.getMessage();
//        } catch (IOException e) {
//            e.printStackTrace();
//            return "模型地址URL无效 "+e.getMessage();
//        }catch (IllegalArgumentException e){
//            e.printStackTrace();
//            return "模型地址URL无效 "+e.getMessage();
//        }catch (Exception e){
//            return "模型地址URL无效 "+e.getMessage();
//        }

        logger.info("开始检测url及模型的有效性");
        String targetPath = testModelDir + "/saved_model.pb";
        RandomAccessFile oSavedFile = null;
        try {
            Path target = Paths.get(targetPath);
            Files.createDirectories(target.getParent());
            oSavedFile = new RandomAccessFile(targetPath, "rw");

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        int total = 0;
        int oldTotal=total;
        while (true) {
            //测试输入的url文件是否有效,是否能够下载
            try {
                URL url = new URL(modelURL);
                URLConnection connection = url.openConnection();
                Path target = Paths.get(targetPath);
                Files.createDirectories(target.getParent());
                //每次下载20m
                connection.setRequestProperty("RANGE", "bytes=" + total + "-" + (total + 20971519));


                try(InputStream is = connection.getInputStream()) {
                    byte[] b = new byte[8096];
                    int nRead;
                    oSavedFile.seek(total);
                    int num=0;
                    while ((nRead = is.read(b, 0, 8096)) > 0) {
                        oSavedFile.write(b, 0, nRead);
                        total += nRead;
                        num++;
                        //手动抛出异常，模拟下载时出错，激发断点续传
//                        if(num==2469){
//                            throw new Exception("手动异常，检测断点续传。已下载字节数"+total);
//                        }
                    }

                    if (total == oldTotal) {
                        logger.info("下载结束，共"+total+"字节");
                        break;
                    }
                    logger.info("下载模型部分字节："+oldTotal + "-" + total);
                    oldTotal = total;

                }catch (Exception e){
                    logger.info(e.getMessage());
                    throw new Exception("传输异常");
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return "模型地址URL不正确 " + e.getMessage();
            } catch (IOException e) {
                e.printStackTrace();
                return "模型地址URL无效 " + e.getMessage();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                return "模型地址URL无效 " + e.getMessage();
            } catch (Exception e) {
                if(e.getMessage().equals("传输异常")){
                    //doNothing 说明是传输问题，直接进行断点续传
                }else return "模型地址URL无效 " + e.getMessage();
            }finally {
                try {
                    oSavedFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        //测试模型是否能有效加载
        try(SavedModelBundle model=SavedModelBundle.load(testModelDir, "serve")){
            //删除这次测试模型文件，以防下次测试时留存的模型文件过长导致末尾字节仍然留存
            File targetFile=new File(targetPath);
            targetFile.delete();
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
