package application;

import java.util.LinkedList;
import java.util.List;

public class DetectResult {
    private String imageURL;
    private List<String> detectCells=new LinkedList<>();

    public String getImageURL() {
        return imageURL;
    }

    public void setImageURL(String imageURL) {
        this.imageURL = imageURL;
    }

    public List<String> getDetectCells() {
        return detectCells;
    }

    public void setDetectCells(List<String> detectCells) {
        this.detectCells = detectCells;
    }
}
