package application.service;

import static object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMap;
import static object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMapItem;
import application.model.DetectResult;
import application.model.Status;
import application.utils.Utils;
import com.google.protobuf.TextFormat;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;
import org.tensorflow.types.UInt8;

/**
 * Java inference for the Object Detection API at:
 * https://github.com/tensorflow/models/blob/master/research/object_detection/
 */

@Component
public class DetectService {
    private static Logger logger = LoggerFactory.getLogger(DetectService.class);

    private static ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    @Value("${filepath.relative.modelDir}")
    private String modelDir;
    @Value("${filepath.relative.modelDownloadDir}")
    private String modelDownloadDir;
    @Value("${filepath.relative.labels}")
    private String labelsPath;

    private String modelURL = null;

    private volatile SavedModelBundle model;

    private String[] labels;

    private String updateTime;

    public void reload() {
        SavedModelBundle oldModel = model;
        logger.info("tensorflow api 版本： " + TensorFlow.version());
        try {
            logger.info("加载label from " + labelsPath);
            labels = loadLabels(labelsPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (modelURL == null) {
                logger.info("加载model from " + modelDir);
                model = SavedModelBundle.load(modelDir, "serve");
            } else {
                logger.info("加载model from " + modelDownloadDir);
                model = SavedModelBundle.load(modelDownloadDir, "serve");
                //删除下载的模型，以防下一次下载的模型小于上一次的，导致就模型结尾保留
                String targetPath = modelDownloadDir + "/saved_model.pb";
                File file=new File(targetPath);
                file.delete();
            }
            logger.info("加载模型成功");
            updateTime = Utils.getFormedDate();
        } catch (Exception e) {
            e.printStackTrace();
            logger.info("模型文件无效，请输入tag为serve的有效模型地址");
        }

        //这一段实际在控制，只要还有线程在使用旧的model，就不能释放旧的model。
        rwl.writeLock().lock();
        try {
            if (oldModel != null) {
                //SavedModelBundle需要close来释放资源
                //同时，就因为这个close。
                //必须采用读写锁
                //因为，你重新载入一个model，需要把原来的modelclose掉，这样才不会内存溢出等
                //但是，如果你立即close，还依赖之前的model的线程就没办法使用之前的model了
                //只能通过读写锁，想要close的时候必须有写锁
                logger.info("释放老的model");
                oldModel.close();
                oldModel = null;
            }
        } finally {
            rwl.writeLock().unlock();
        }
    }

    public List<DetectResult> doService(List<String> imageURLs) throws Exception {
        //还有线程使用旧的model时，不允许close旧的model
        rwl.readLock().lock();
        try {
            if (imageURLs.size() < 1) {
                printUsage(System.err);
            }
            SavedModelBundle workModel = null;


            while (model == null || labels == null) {
                logger.info("等待模型加载完成for 2 秒");
                Thread.sleep(2000);
            }

            workModel = model;

            List<DetectResult> detectResults = new LinkedList<>();


            printSignature(workModel);
            for (int imgindex = 0; imgindex < imageURLs.size(); imgindex++) {
                DetectResult detectResult = new DetectResult();

                final String filename = imageURLs.get(imgindex);
                List<Tensor<?>> outputs = null;
                try (Tensor<UInt8> input = makeImageTensor(filename)) {
                    outputs =
                            workModel
                                    .session()
                                    .runner()
                                    .feed("image_tensor", input)
                                    .fetch("detection_scores")
                                    .fetch("detection_classes")
                                    .fetch("detection_boxes")
                                    .run();
                }
                try (Tensor<Float> scoresT = outputs.get(0).expect(Float.class);
                     Tensor<Float> classesT = outputs.get(1).expect(Float.class);
                     Tensor<Float> boxesT = outputs.get(2).expect(Float.class)) {
                    // All these tensors have:
                    // - 1 as the first dimension
                    // - maxObjects as the second dimension
                    // While boxesT will have 4 as the third dimension (2 sets of (x, y) coordinates).
                    // This can be verified by looking at scoresT.shape() etc.
                    int maxObjects = (int) scoresT.shape()[1];
                    float[] scores = scoresT.copyTo(new float[1][maxObjects])[0];
                    float[] classes = classesT.copyTo(new float[1][maxObjects])[0];
                    float[][] boxes = boxesT.copyTo(new float[1][maxObjects][4])[0];
                    // Print all objects whose score is at least 0.5.
                    System.out.printf("* %s\n", filename);

                    detectResult.setImageURL(filename);

                    boolean foundSomething = false;
                    for (int i = 0; i < scores.length; ++i) {
                        if (scores[i] < 0.5) {
                            continue;
                        }
                        foundSomething = true;
                        detectResult.getDetectCells().add(String.format("Found %s (score: %.4f)", labels[(int) classes[i]], scores[i]));
                        System.out.printf("\tFound %-20s (score: %.4f)\n", labels[(int) classes[i]], scores[i]);
                    }
                    detectResults.add(detectResult);
                    if (!foundSomething) {
                        System.out.println("No objects detected with a high enough score.");
                    }
                }
            }

            return detectResults;
        }finally {
            rwl.readLock().unlock();
        }
    }

    private static void printSignature(SavedModelBundle model) throws Exception {
        MetaGraphDef m = MetaGraphDef.parseFrom(model.metaGraphDef());
        SignatureDef sig = m.getSignatureDefOrThrow("serving_default");
        int numInputs = sig.getInputsCount();
        int i = 1;
        System.out.println("MODEL SIGNATURE");
        System.out.println("Inputs:");
        for (Map.Entry<String, TensorInfo> entry : sig.getInputsMap().entrySet()) {
            TensorInfo t = entry.getValue();
            System.out.printf(
                    "%d of %d: %-20s (Node name in graph: %-20s, type: %s)\n",
                    i++, numInputs, entry.getKey(), t.getName(), t.getDtype());
        }
        int numOutputs = sig.getOutputsCount();
        i = 1;
        System.out.println("Outputs:");
        for (Map.Entry<String, TensorInfo> entry : sig.getOutputsMap().entrySet()) {
            TensorInfo t = entry.getValue();
            System.out.printf(
                    "%d of %d: %-20s (Node name in graph: %-20s, type: %s)\n",
                    i++, numOutputs, entry.getKey(), t.getName(), t.getDtype());
        }
        System.out.println("-----------------------------------------------");
    }

    private static String[] loadLabels(String filename) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
        StringIntLabelMap.Builder builder = StringIntLabelMap.newBuilder();
        TextFormat.merge(text, builder);
        StringIntLabelMap proto = builder.build();
        int maxId = 0;
        for (StringIntLabelMapItem item : proto.getItemList()) {
            if (item.getId() > maxId) {
                maxId = item.getId();
            }
        }
        String[] ret = new String[maxId + 1];
        for (StringIntLabelMapItem item : proto.getItemList()) {
            ret[item.getId()] = item.getDisplayName();
        }
        return ret;
    }

    private static void bgr2rgb(byte[] data) {
        for (int i = 0; i < data.length; i += 3) {
            byte tmp = data[i];
            data[i] = data[i + 2];
            data[i + 2] = tmp;
        }
    }

    private static Tensor<UInt8> makeImageTensor(String filename) throws IOException {
//    BufferedImage img = ImageIO.read(new File(filename));//从文件路径读
        BufferedImage img = ImageIO.read(new URL(filename));//从URL读
        if (img.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            throw new IOException(
                    String.format(
                            "Expected 3-byte BGR encoding in BufferedImage, found %d (file: %s). This code could be made more robust",
                            img.getType(), filename));
        }
        byte[] data = ((DataBufferByte) img.getData().getDataBuffer()).getData();
        // ImageIO.read seems to produce BGR-encoded images, but the model expects RGB.
        bgr2rgb(data);
        final long BATCH_SIZE = 1;
        final long CHANNELS = 3;
        long[] shape = new long[]{BATCH_SIZE, img.getHeight(), img.getWidth(), CHANNELS};
        return Tensor.create(UInt8.class, shape, ByteBuffer.wrap(data));
    }

    public Status staus(Status status) {
        status.setUpdateTime(updateTime);
        status.setModelURL(this.modelURL == null ? "使用预置model" : this.modelURL);
        return status;
    }

    private static void printUsage(PrintStream s) {
        s.println("USAGE: <model> <label_map> <image> [<image>] [<image>]");
        s.println("");
        s.println("Where");
        s.println("<model> is the path to the SavedModel directory of the model to use.");
        s.println("        For example, the saved_model directory in tarballs from ");
        s.println(
                "        https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md)");
        s.println("");
        s.println(
                "<label_map> is the path to a file containing information about the labels detected by the model.");
        s.println("            For example, one of the .pbtxt files from ");
        s.println(
                "            https://github.com/tensorflow/models/tree/master/research/object_detection/data");
        s.println("");
        s.println("<image> is the path to an image file.");
        s.println("        Sample images can be found from the COCO, Kitti, or Open Images dataset.");
        s.println(
                "        See: https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md");
        s.println("");
        s.println("");
        s.println("上面是tensorflow源码的使用方式.");
        s.println("在这里,imageURLs要求是图片的url列表的封装");
    }

    //在这里加锁，保证一次只有一个在下载和写文件，要是同时写文件，有锁
    public synchronized void updateModelFromURL(String modelURL) {
        //如果模型还没有加载过，则忽略加载命令
        //如果不忽略，就加载了两个模型，并且其中一个没有办法被close，就不再有引用指向
        if(model==null){
            logger.warn("忽略模型加载命令，因为应用启动时的加载还未完成");
            return;
        }

        //断点续传版本  不支持对象存储的http链接
        logger.info("开始检测url及模型的有效性");
        String targetPath = modelDownloadDir + "/saved_model.pb";
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
                connection.setRequestProperty("Range", "bytes=" + total + "-" + (total + 20971519));

                try(InputStream is = connection.getInputStream()) {
                    byte[] b = new byte[8096];
                    int nRead;
                    oSavedFile.seek(total);
                    while ((nRead = is.read(b, 0, 8096)) > 0) {
                        oSavedFile.write(b, 0, nRead);
                        total += nRead;
                    }

                    if (total == oldTotal) {
                        logger.info("下载结束，共"+total+"字节");
                        oSavedFile.close();
                        break;
                    }
                    logger.info("下载模型部分字节："+oldTotal + "-" + total);
                    oldTotal = total;

                }catch (Exception e){
                    System.out.println(e.getMessage());
                    throw new Exception("传输异常");
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
                System.out.println( "模型地址URL不正确 " + e.getMessage());
                return;
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println( "模型地址URL无效 " + e.getMessage());
                return;
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                System.out.println( "模型地址URL无效 " + e.getMessage());
                return;
            } catch (Exception e) {
                if(e.getMessage().equals("传输异常")){
                    //doNothing 说明是传输问题，直接进行断点续传
                }else {
                    System.out.println("模型地址URL无效 " + e.getMessage());
                    return;
                }
            }
        }

        this.modelURL = modelURL;
        reload();
    }
}
