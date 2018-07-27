package application.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {
    public static String getFormedDate(){
        SimpleDateFormat simpleDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return simpleDate.format(new Date());
    }
}
