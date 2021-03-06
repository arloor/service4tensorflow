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

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

@RestController
@RequestMapping("/model")
public class ModelController {
    private int pianLength=3145728;
//    private int pianLength=8096;

    @Value("${filepath.relative.testModelDir}")
    private String testModelDir;

    @Value("${filepath.relative.toDownloadModelFile}")
    private String toDownloadModelFile;

    private static Logger logger = LoggerFactory.getLogger(ModelController.class);

    private String modelURL = "https://api.moontell.cn:9000/model/modelfile";

    @Autowired
    RabbitmqHelper rabbitmqHelper;

    @RequestMapping("/updateSingleModel")
    public String updateSingleModel(@RequestParam String nodeName, @RequestParam String modelURL) {
        String result = testModelUrl(modelURL);
        if (result.equals("valid")) {
            this.setModelURL(modelURL);
            //通知所有节点更新模型5
            rabbitmqHelper.send(nodeName + "::update::" + modelURL);
            return "已向" + nodeName + "发送更新模型命令,更新后的模型地址将会是 " + modelURL;
        } else {
            return result;
        }
    }

    @RequestMapping("/updateAllModel")
    public String updateAllModel(@RequestParam String modelURL,
                                 org.apache.catalina.servlet4preview.http.HttpServletRequest request) {
        String result = testModelUrl(modelURL);
        if (result.equals("valid")) {
            this.setModelURL(modelURL);
            //通知所有节点更新模型5
            rabbitmqHelper.send("all::update::" + modelURL);
            return "已向所有节点发送更新模型命令";
        } else {
            return result;
        }
    }

    @RequestMapping("/modelfile")
    public  byte[] model(HttpServletRequest request, HttpServletResponse response){
        CRC32 checksumTool=new CRC32();
        try(RandomAccessFile model=new RandomAccessFile(toDownloadModelFile,"r")){
            byte[] readBytes=new byte[pianLength];
            long length=model.length();
            String range=request.getHeader("Range");
            if(range!=null){
                //bytes=0-20971519
                String temp=range.split("=")[1];
                long start=Long.parseLong(temp.split("-")[0]);
                long end=Long.parseLong(temp.split("-")[1]);
                model.seek(start);
                int n=0;
                do {
                    int readNum=model.read(readBytes,n,(int)(end-start+1-n));
                    if(readNum==-1){
                        break;
                    }
                    n+=readNum;
                }while (n<end-start+1);
                end=start+n-1;

                //todo：校验算法。。
                int sumcheck=0;
                for (int i = 0; i < 3000; i++) {
                    sumcheck+=(int)(readBytes[i*1000]);
                }
//                System.out.println(sumcheck);

                checksumTool.update(readBytes);
                String checksumStr=String.valueOf(sumcheck);
                logger.info("Content-Range: "+"bytes "+start+"-"+end+"/"+length+"  Set-Cookie: checksum="+checksumStr);
                response.addCookie(new Cookie("checksum",checksumStr));
                response.setHeader("Content-Range","bytes "+start+"-"+end+"/"+length);
                if(n==pianLength){
                    return readBytes;
                }else{
                    byte[] result=new byte[n];
                    System.arraycopy(readBytes,0,result,0,n);
                    return result;
                }
            }else{
                response.sendError(400);
                return null;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    //在这里加锁，保证一次只有一个在下载和写文件，要是同时写文件，有文件锁
    private synchronized String testModelUrl(String modelURL) {
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
        int tryTimes=0;
        final int maxTryTime=5;
        CRC32 checksumTool=new CRC32();
        while (true) {
            //测试输入的url文件是否有效,是否能够下载
            try {
                URL url = new URL(modelURL);
                URLConnection connection = url.openConnection();
                Path target = Paths.get(targetPath);
                Files.createDirectories(target.getParent());
                //每次下载5m
                connection.setRequestProperty("Range", "bytes=" + total + "-" + (total + pianLength-1));

                try (InputStream is = connection.getInputStream()) {
                    String contentRange = connection.getHeaderField("Content-Range");
                    long start=Long.parseLong(contentRange.substring(contentRange.indexOf(" ")+1,contentRange.indexOf("-")));
                    long end=Long.parseLong(contentRange.substring(contentRange.indexOf("-")+1,contentRange.indexOf("/")));
                    String setCookie = connection.getHeaderField("Set-Cookie");
                    String checkSum=null;
                    if(setCookie!=null){
                        checkSum=setCookie.split("=")[1];
                    }
                    //byteSum是文件的字节长度，通过它与total（下载总数）比较判断是否下载完毕。
                    int bytesNum = Integer.parseInt(contentRange.substring(contentRange.indexOf("/") + 1));
                    byte[] b = new byte[pianLength];
                    int nRead=0;
                    int n=0;
                    oSavedFile.seek(total);
                    while ((nRead = is.read(b, n, (int)(end-start+1-n))) > 0) {
                        n+=nRead;
                    }
                    checksumTool.update(b);
                    //todo：校验算法。。
                    int sumcheck=0;
                    for (int i = 0; i < 3000; i++) {
                        sumcheck+=(int)(b[i*1000]);
                    }
//                    System.out.println(sumcheck);

                    oSavedFile.write(b, 0, n);
                    total += n;

                    logger.info("下载分片： " + contentRange+" 已读: "+((double)total/1048576)+"M checksum: "+(checkSum==null?"无":checkSum)+" ?= "+sumcheck);

                    if (total == bytesNum) {
                        logger.info("下载结束，共" + total + "字节");
                        oSavedFile.close();
                        break;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
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
                if (e.getMessage().equals("传输异常")) {
                    tryTimes++;
                    if(tryTimes==maxTryTime){
                        //删除这次测试模型文件，以防下次测试时留存的模型文件过长导致末尾字节仍然留存
                        logger.info("网络传输或url有问题，已重复"+maxTryTime+"次，退出！");
                        File targetFile = new File(targetPath);
                        boolean deleted=targetFile.delete();
                        logger.info("删除下载的模型：" +deleted);
                        return "所填写的url有误或网络存在问题，重复"+maxTryTime+"次仍出现问题，已放弃！";
                    }
                    //doNothing 说明是传输问题，直接进行断点续传
                } else return "模型地址URL无效 " + e.getMessage();
            }
        }

        //测试模型是否能有效加载
        try (SavedModelBundle model = SavedModelBundle.load(testModelDir, "serve")) {
            return "valid";
        } catch (org.tensorflow.TensorFlowException e) {
            return "模型文件无效，请输入tag为serve的有效模型地址";
        }finally {
            //删除这次测试模型文件，以防下次测试时留存的模型文件过长导致末尾字节仍然留存
            File targetFile = new File(targetPath);
            boolean deleted=targetFile.delete();
            logger.info("删除下载的模型：" +deleted);
        }
    }


    @RequestMapping("/modelURL")
    public String defaultModelURL() {
        return modelURL;
    }

    private void setModelURL(String modelURL) {
        this.modelURL = modelURL;
    }
}
