package obcontrol.SocketHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.Adler32;
import java.util.zip.Checksum;


public class ServerSocketHandler implements Runnable{
    private Checksum checksum=new Adler32();

    private final static Logger logger=LoggerFactory.getLogger(ServerSocketHandler.class);
    private final static ServerSocketHandler instance=new ServerSocketHandler();

    private ServerSocketChannel serverChannel;
    private Selector serverSelector;

    private ServerSocketHandler(){
        try {
            this.serverSelector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 9001));
            serverChannel.configureBlocking(false);
            serverChannel.register(serverSelector, SelectionKey.OP_ACCEPT);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static ServerSocketHandler getInstance(){
        return instance;
    }

    public void listen(){
        RandomAccessFile modelFile=null;
        try {
            modelFile=new RandomAccessFile("tensor_model/objectdetect/models/obdect_model/saved_model/saved_model_for_download_by_socket.pb","r");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        while (true) {
            //循环selctor
            try {
                logger.warn("selector 开始监听socket下载请求");
                int numKey = serverSelector.select();
                if (numKey > 0) {

                    Set<SelectionKey> keys = serverSelector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                            if (key.isReadable()) {
                                SocketChannel channel=(SocketChannel)key.channel();
                                ByteBuffer buffer=(ByteBuffer)key.attachment();
                                try {
                                    int readNum=channel.read(buffer);
                                    //download\r\nstart
                                    if(readNum>0){
                                        //0-4096这种
                                        byte[] readBytes=new byte[readNum];
                                        System.arraycopy(buffer.array(),0,readBytes,0,readNum);
                                        String socketMsg=new String(readBytes);
                                        buffer.clear();
                                        processSocketMsg(socketMsg,channel,modelFile,buffer,key);

                                    }else if(readNum==0){
                                        logger.warn("读到0个字节！ wtf？");
                                    }else if(readNum==-1){
                                        logger.warn("读到-1");
                                        channel.close();
                                        key.cancel();
                                    }
                                    buffer.clear();
                                }catch (IOException e){
                                    logger.warn(e.getMessage()+"?==远程主机强迫关闭了一个现有的连接。");
                                    channel.close();
                                    key.cancel();
                                }
                            }else if (key.isAcceptable()) {
                                SocketChannel chanel = serverChannel.accept();
                                chanel.configureBlocking(false);
                                ByteBuffer buffer=ByteBuffer.allocate(6000);
                                chanel.register(serverSelector,SelectionKey.OP_READ,buffer);
                            }
                        keyIterator.remove();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processSocketMsg(String socketMsg, SocketChannel channel, RandomAccessFile modelFile, ByteBuffer buffer, SelectionKey key) {
        String[] ranges=socketMsg.split("\r\n");

        for (String range:ranges
             ) {
            logger.info(range);
            if(range.equals("start")){
                try {
                    long length=modelFile.length();
                    buffer.put(("Length: "+String.valueOf(length)).getBytes());
                    buffer.flip();
                    channel.write(buffer);
                    buffer.clear();

                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }

            long start=Long.parseLong(range.split("-")[0]);
            try {
                if(start>modelFile.length()){
                    channel.close();
                    key.cancel();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            long stop=Long.parseLong(range.split("-")[1]);
            try {
                stop=stop>modelFile.length()?modelFile.length():stop;
            } catch (IOException e) {
                e.printStackTrace();
            }
            int length=(int)(stop-start+1);
            try {
                modelFile.seek(start);
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                int n = 0;
                do {
                    int count = modelFile.read(buffer.array(), 0 + n, length - n);
                    if (count < 0)
                        break;
                    n += count;
                } while (n < length);
                if(n==0){
                    channel.close();
                    key.cancel();
                }
                buffer.position(0);
                checksum.update(buffer.array(),0,n);
                logger.info("Length:"+n+" CheckSum:"+checksum.getValue()+" Range:"+range);
                String header="Length:"+n+"\r\nCheckSum:"+checksum.getValue()+"\r\nRange:"+range+"\r\n\r\n";
                System.arraycopy(buffer.array(),0,buffer.array(),header.length(),n);
                System.arraycopy(header.getBytes(),0,buffer.array(),0,header.length());
                buffer.limit(n+header.length());

                channel.write(buffer);
            }catch (IndexOutOfBoundsException e){
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void run() {
        listen();
    }
}
