package obcontrol;


import obcontrol.SocketHelper.ServerSocketHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ObcontrolApplication {
    public static void main(String[] args){
        SpringApplication.run(ObcontrolApplication.class);
        new Thread(ServerSocketHandler.getInstance(),"socketHelper").run();
    }
}
