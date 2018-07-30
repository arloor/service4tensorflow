package obcontrol.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {
    private static SimpleDateFormat simpleDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static String getFormedDate(){
        return simpleDate.format(new Date());
    }

    public static Date formDate(String dateStr){
        try {
            return simpleDate.parse(dateStr);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
}
