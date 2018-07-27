package application.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {
    public static String getFormedDate(){
        SimpleDateFormat simpleDate = new SimpleDateFormat("YYYY-MM-DD HH:MM:SS");
        return simpleDate.format(new Date());
    }
}
